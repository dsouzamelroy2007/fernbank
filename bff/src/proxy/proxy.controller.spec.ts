import { HttpService } from '@nestjs/axios';
import { ValidationPipe, type INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import cookieParser from 'cookie-parser';
import { of } from 'rxjs';
import request from 'supertest';
import type { App } from 'supertest/types';
import { AppModule } from '../app.module';
import { CSRF_COOKIE_NAME } from '../common/csrf-cookie';
import {
  createMockHttpService,
  extractCookieValue,
  fakeJwt,
} from '../test-support/mock-http-service';

const PASSWORD = 'correct horse battery staple';

describe('ProxyController (integration)', () => {
  let app: INestApplication<App>;
  let mockHttp: ReturnType<typeof createMockHttpService>;

  beforeEach(async () => {
    mockHttp = createMockHttpService((config) => {
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
        return { status: 200, body: [{ id: 'acc-1', accountNumber: 'FB123' }] };
      }
      if (config.url.includes('/api/v1/accounts/acc-1/statement')) {
        return { status: 200, body: { data: [], hasNext: false } };
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

  async function loginAgent() {
    const agent = request.agent(app.getHttpServer());
    const loginResponse = await agent
      .post('/api/v1/auth/login')
      .send({ email: 'ada@example.com', password: PASSWORD })
      .expect(200);
    const csrfToken = extractCookieValue(
      loginResponse.headers['set-cookie'] as unknown as string[],
      CSRF_COOKIE_NAME,
    );
    return { agent, csrfToken };
  }

  it('forwards an allowlisted bare-resource GET request', async () => {
    const { agent } = await loginAgent();
    const response = await agent.get('/api/v1/accounts').expect(200);
    expect(response.body).toEqual([{ id: 'acc-1', accountNumber: 'FB123' }]);
  });

  it('forwards an allowlisted sub-path GET request', async () => {
    const { agent } = await loginAgent();
    const response = await agent
      .get('/api/v1/accounts/acc-1/statement')
      .expect(200);
    expect(response.body).toEqual({ data: [], hasNext: false });
  });

  it('forwards the Idempotency-Key header on a mutating request', async () => {
    const { agent, csrfToken } = await loginAgent();
    const capturedHeaders: Array<Record<string, unknown>> = [];
    mockHttp.request.mockImplementation(
      (config: {
        url: string;
        method?: string;
        headers?: Record<string, unknown>;
      }) => {
        if (config.url.endsWith('/api/v1/payees') && config.method === 'POST') {
          capturedHeaders.push(config.headers ?? {});
          const buf = Buffer.from(JSON.stringify({ id: 'payee-1' }), 'utf8');
          return of({
            data: buf.buffer.slice(
              buf.byteOffset,
              buf.byteOffset + buf.byteLength,
            ),
            status: 201,
            headers: { 'content-type': 'application/json' },
          });
        }
        throw new Error(
          `Unhandled mock call in override: ${config.method} ${config.url}`,
        );
      },
    );

    await agent
      .post('/api/v1/payees')
      .set('Idempotency-Key', 'test-idempotency-key-123')
      .set('X-CSRF-Token', csrfToken)
      .send({ name: 'Dana', targetAccountNumber: 'FB456' })
      .expect(201);

    expect(capturedHeaders[0]['idempotency-key']).toBe(
      'test-idempotency-key-123',
    );
  });

  it('returns 404 for a path outside the allowlist rather than proxying it', async () => {
    const { agent } = await loginAgent();
    await agent.get('/api/v1/admin/reconciliation').expect(404);
  });

  it('rejects a proxied request with no session', async () => {
    await request(app.getHttpServer()).get('/api/v1/accounts').expect(401);
  });
});
