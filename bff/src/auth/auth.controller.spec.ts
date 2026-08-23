import { HttpService } from '@nestjs/axios';
import { ValidationPipe, type INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import cookieParser from 'cookie-parser';
import request from 'supertest';
import type { App } from 'supertest/types';
import { AppModule } from '../app.module';
import { CSRF_COOKIE_NAME } from '../common/csrf-cookie';
import { SESSION_COOKIE_NAME } from '../common/session-cookie';
import {
  createMockHttpService,
  extractCookieValue,
  fakeJwt,
} from '../test-support/mock-http-service';

const PASSWORD = 'correct horse battery staple';

describe('AuthController (integration)', () => {
  let app: INestApplication<App>;

  beforeEach(async () => {
    const mockHttp = createMockHttpService((config) => {
      if (config.url.endsWith('/api/v1/auth/login')) {
        const body = config.data as { email: string; password: string };
        if (body.password !== PASSWORD) {
          return { status: 401, body: { title: 'Invalid credentials' } };
        }
        if (body.email === 'mfa@example.com') {
          return {
            status: 200,
            body: { status: 'MFA_REQUIRED', mfaToken: 'mfa-token-xyz' },
          };
        }
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

  it('sets session and CSRF cookies on a successful login, and never returns a token', async () => {
    const response = await request(app.getHttpServer())
      .post('/api/v1/auth/login')
      .send({ email: 'ada@example.com', password: PASSWORD })
      .expect(200);

    const body = response.body as Record<string, unknown>;
    expect(body).toEqual({ status: 'AUTHENTICATED' });
    expect(body.accessToken).toBeUndefined();
    expect(body.refreshToken).toBeUndefined();
    expect(JSON.stringify(body)).not.toContain('refresh-token-1');

    const cookies = response.headers['set-cookie'] as unknown as string[];
    expect(
      cookies.some(
        (c) => c.startsWith(`${SESSION_COOKIE_NAME}=`) && /HttpOnly/i.test(c),
      ),
    ).toBe(true);
    expect(
      cookies.some(
        (c) => c.startsWith(`${CSRF_COOKIE_NAME}=`) && !/HttpOnly/i.test(c),
      ),
    ).toBe(true);
  });

  it('sets no cookies on an MFA_REQUIRED result', async () => {
    const response = await request(app.getHttpServer())
      .post('/api/v1/auth/login')
      .send({ email: 'mfa@example.com', password: PASSWORD })
      .expect(200);

    expect(response.body).toEqual({
      status: 'MFA_REQUIRED',
      mfaToken: 'mfa-token-xyz',
    });
    expect(response.headers['set-cookie']).toBeUndefined();
  });

  it('rejects a wrong password without setting any cookie', async () => {
    const response = await request(app.getHttpServer())
      .post('/api/v1/auth/login')
      .send({ email: 'ada@example.com', password: 'wrong' })
      .expect(401);

    expect(response.headers['set-cookie']).toBeUndefined();
  });

  it('logout clears both cookies (once the CSRF token is presented)', async () => {
    const agent = request.agent(app.getHttpServer());
    const loginResponse = await agent
      .post('/api/v1/auth/login')
      .send({ email: 'ada@example.com', password: PASSWORD })
      .expect(200);

    const csrfToken = extractCookieValue(
      loginResponse.headers['set-cookie'] as unknown as string[],
      CSRF_COOKIE_NAME,
    );

    const response = await agent
      .post('/api/v1/auth/logout')
      .set('X-CSRF-Token', csrfToken)
      .expect(204);
    const cookies = response.headers['set-cookie'] as unknown as string[];
    expect(cookies.some((c) => c.startsWith(`${SESSION_COOKIE_NAME}=;`))).toBe(
      true,
    );
    expect(cookies.some((c) => c.startsWith(`${CSRF_COOKIE_NAME}=;`))).toBe(
      true,
    );
  });

  it('rejects logout without a matching CSRF header', async () => {
    const agent = request.agent(app.getHttpServer());
    await agent
      .post('/api/v1/auth/login')
      .send({ email: 'ada@example.com', password: PASSWORD })
      .expect(200);

    await agent.post('/api/v1/auth/logout').expect(403);
  });
});
