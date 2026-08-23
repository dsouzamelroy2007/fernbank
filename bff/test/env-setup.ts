// Runs before any module import in every Jest suite (unit + e2e) — config/configuration.ts
// validates these at import time, so they must exist before that module is first required.
process.env.BACKEND_INTERNAL_BASE_URL ??= 'http://localhost:8080';
process.env.BFF_CORS_ALLOWED_ORIGINS ??= 'http://localhost:3000';
process.env.BFF_SESSION_ENCRYPTION_KEY ??= Buffer.alloc(32, 7).toString(
  'base64',
);
