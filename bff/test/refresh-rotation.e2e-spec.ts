import { HttpService } from '@nestjs/axios';
import { ValidationPipe, type INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import cookieParser from 'cookie-parser';
import request from 'supertest';
import type { App } from 'supertest/types';
import { AppModule } from '../src/app.module';
import {
  createMockHttpService,
  fakeJwt,
} from '../src/test-support/mock-http-service';

const PASSWORD = 'correct horse battery staple';

/**
 * The scenario the access-token-cache unit test proves at the direct-method-call
 * level; this proves it holds under REAL concurrent HTTP requests through the full
 * Express/Nest stack (middleware, guards, interceptor) — confirming
 * AccessTokenCacheService really is a process-wide singleton, not accidentally
 * request-scoped by some layer in between.
 */
describe('Refresh rotation under real request concurrency (e2e)', () => {
  let app: INestApplication<App>;
  let refreshCallCount: number;

  beforeEach(async () => {
    refreshCallCount = 0;
    const mockHttp = createMockHttpService((config) => {
      if (config.url.endsWith('/api/v1/auth/login')) {
        // Seeded with a token that's already within the default 30s refresh skew, so
        // the very next proxied call must trigger a refresh.
        return {
          status: 200,
          body: {
            status: 'AUTHENTICATED',
            accessToken: fakeJwt(2),
            refreshToken: 'refresh-token-0',
          },
        };
      }
      if (config.url.endsWith('/api/v1/auth/refresh')) {
        refreshCallCount++;
        return {
          status: 200,
          body: {
            accessToken: fakeJwt(600),
            refreshToken: `refresh-token-${refreshCallCount}`,
          },
        };
      }
      if (config.url.endsWith('/api/v1/accounts')) {
        return { status: 200, body: [] };
      }
      throw new Error(`Unhandled mock call: ${config.method} ${config.url}`);
    });

    const moduleRef = await Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(HttpService)
      .useValue(mockHttp)
      .compile();

    app = moduleRef.createNestApplication();
    app.use(cookieParser());
    app.useGlobalPipes(
      new ValidationPipe({ whitelist: true, transform: true }),
    );
    await app.init();
  });

  afterEach(async () => {
    await app.close();
  });

  it('single-flights concurrent proxied requests into exactly one refresh call', async () => {
    const agent = request.agent(app.getHttpServer());
    await agent
      .post('/api/v1/auth/login')
      .send({ email: 'ada@example.com', password: PASSWORD })
      .expect(200);

    const responses = await Promise.all([
      agent.get('/api/v1/accounts'),
      agent.get('/api/v1/accounts'),
      agent.get('/api/v1/accounts'),
    ]);

    for (const response of responses) {
      expect(response.status).toBe(200);
    }
    expect(refreshCallCount).toBe(1);
  });
});
