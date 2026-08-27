import { HttpService } from '@nestjs/axios';
import type { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import type { App } from 'supertest/types';
import { AppModule } from '../app.module';
import { createMockHttpService } from '../test-support/mock-http-service';

describe('WarmupController (integration)', () => {
  let app: INestApplication<App>;

  async function buildApp(
    handler: Parameters<typeof createMockHttpService>[0],
  ) {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(HttpService)
      .useValue(createMockHttpService(handler))
      .compile();
    app = moduleRef.createNestApplication();
    await app.init();
  }

  afterEach(async () => {
    await app.close();
  });

  it('returns 200 UP when the backend health check is UP', async () => {
    await buildApp((config) => {
      if (config.url.endsWith('/actuator/health')) {
        return { status: 200, body: { status: 'UP' } };
      }
      throw new Error(`Unhandled mock call: ${config.method} ${config.url}`);
    });

    await request(app.getHttpServer())
      .get('/api/v1/warmup')
      .expect(200, { status: 'UP' });
  });

  it('returns 503 when the backend health check reports DOWN', async () => {
    await buildApp((config) => {
      if (config.url.endsWith('/actuator/health')) {
        return { status: 200, body: { status: 'DOWN' } };
      }
      throw new Error(`Unhandled mock call: ${config.method} ${config.url}`);
    });

    await request(app.getHttpServer()).get('/api/v1/warmup').expect(503);
  });

  it('returns 503 when the backend is unreachable', async () => {
    await buildApp(() => {
      throw new Error('connect ECONNREFUSED');
    });

    await request(app.getHttpServer()).get('/api/v1/warmup').expect(503);
  });
});
