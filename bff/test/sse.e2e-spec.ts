import { HttpService } from '@nestjs/axios';
import { ValidationPipe, type INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import cookieParser from 'cookie-parser';
import * as http from 'node:http';
import type { AddressInfo } from 'node:net';
import request from 'supertest';
import type { App } from 'supertest/types';
import { AppModule } from '../src/app.module';
import {
  createMockHttpService,
  fakeJwt,
} from '../src/test-support/mock-http-service';

const PASSWORD = 'correct horse battery staple';

/**
 * A real, listening HTTP connection (not supertest's in-process request) is needed
 * here since @Sse() streams over time — this proves the route, CsrfGuard's safe-method
 * exemption, SessionContextMiddleware, and NotificationPollerService all wire together
 * for a real long-lived connection, which nothing at the unit level can catch.
 */
describe('SSE notifications (e2e)', () => {
  let app: INestApplication<App>;
  let port: number;
  let accountsCallCount: number;

  beforeEach(async () => {
    accountsCallCount = 0;
    const mockHttp = createMockHttpService((config) => {
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
      if (config.url.endsWith('/api/v1/accounts')) {
        accountsCallCount++;
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
    await app.listen(0);
    const server = app.getHttpServer() as unknown as http.Server;
    const address = server.address() as AddressInfo | null;
    port = address?.port ?? 0;
  });

  afterEach(async () => {
    await app.close();
  });

  it('establishes a real SSE connection and the poller runs for it', async () => {
    const loginResponse = await request(app.getHttpServer())
      .post('/api/v1/auth/login')
      .send({ email: 'ada@example.com', password: PASSWORD })
      .expect(200);
    const sessionCookie = (
      loginResponse.headers['set-cookie'] as unknown as string[]
    ).find((c) => c.startsWith('fernbank_bff_session='));
    if (!sessionCookie) {
      throw new Error('Login did not set a session cookie');
    }

    await new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => {
        req.destroy();
        reject(
          new Error(
            'Timed out waiting for the SSE connection to open and the poller to run',
          ),
        );
      }, 9000);

      const req = http.request(
        {
          host: 'localhost',
          port,
          path: '/bff/notifications',
          method: 'GET',
          headers: { Cookie: sessionCookie, Accept: 'text/event-stream' },
        },
        (res) => {
          expect(res.statusCode).toBe(200);
          expect(res.headers['content-type']).toContain('text/event-stream');

          const check = setInterval(() => {
            if (accountsCallCount >= 1) {
              clearInterval(check);
              clearTimeout(timeout);
              req.destroy();
              resolve();
            }
          }, 100);
        },
      );
      req.on('error', (err) => {
        clearTimeout(timeout);
        reject(err);
      });
      req.end();
    });

    expect(accountsCallCount).toBeGreaterThanOrEqual(1);
  }, 10_000);
});
