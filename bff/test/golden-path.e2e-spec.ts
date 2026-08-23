import { HttpService } from '@nestjs/axios';
import { ValidationPipe, type INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import cookieParser from 'cookie-parser';
import request from 'supertest';
import type { App } from 'supertest/types';
import { AppModule } from '../src/app.module';
import { CSRF_COOKIE_NAME } from '../src/common/csrf-cookie';
import {
  createMockHttpService,
  extractCookieValue,
  fakeJwt,
} from '../src/test-support/mock-http-service';

const PASSWORD = 'correct horse battery staple';

/** Mirrors scripts/smoke-test-web.sh's shape: register -> login -> a proxied call ->
 * a CSRF-protected mutation (rejected without the header, accepted with it) -> logout
 * -> the same proxied call 401s afterward. */
describe('Golden path (e2e)', () => {
  let app: INestApplication<App>;

  beforeEach(async () => {
    const mockHttp = createMockHttpService((config) => {
      if (config.url.endsWith('/api/v1/auth/register')) {
        return {
          status: 201,
          body: { userId: 'user-1', email: 'ada@example.com' },
        };
      }
      if (config.url.endsWith('/api/v1/auth/login')) {
        return {
          status: 200,
          body: {
            status: 'AUTHENTICATED',
            accessToken: fakeJwt(),
            refreshToken: 'refresh-token-1',
          },
        };
      }
      if (config.url.endsWith('/api/v1/auth/logout')) {
        return { status: 204, body: undefined };
      }
      if (config.url.endsWith('/api/v1/accounts') && config.method === 'GET') {
        return { status: 200, body: [] };
      }
      if (config.url.endsWith('/api/v1/payees') && config.method === 'POST') {
        return { status: 201, body: { id: 'payee-1', name: 'Dana' } };
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

  it('walks register -> login -> proxied GET -> CSRF-gated mutation -> logout -> 401 after logout', async () => {
    await request(app.getHttpServer())
      .post('/api/v1/auth/register')
      .send({
        fullName: 'Ada Lovelace',
        email: 'ada@example.com',
        password: PASSWORD,
      })
      .expect(201);

    const agent = request.agent(app.getHttpServer());
    const loginResponse = await agent
      .post('/api/v1/auth/login')
      .send({ email: 'ada@example.com', password: PASSWORD })
      .expect(200);
    expect(loginResponse.body).toEqual({ status: 'AUTHENTICATED' });
    expect(JSON.stringify(loginResponse.body)).not.toMatch(
      /accessToken|refreshToken/,
    );

    await agent.get('/api/v1/accounts').expect(200);

    await agent
      .post('/api/v1/payees')
      .send({ name: 'Dana', targetAccountNumber: 'FB456' })
      .expect(403);

    const csrfToken = extractCookieValue(
      loginResponse.headers['set-cookie'] as unknown as string[],
      CSRF_COOKIE_NAME,
    );
    await agent
      .post('/api/v1/payees')
      .set('X-CSRF-Token', csrfToken)
      .send({ name: 'Dana', targetAccountNumber: 'FB456' })
      .expect(201);

    await agent
      .post('/api/v1/auth/logout')
      .set('X-CSRF-Token', csrfToken)
      .expect(204);

    await agent.get('/api/v1/accounts').expect(401);
  });
});
