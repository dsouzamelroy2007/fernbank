# Threat model

> Status: complete. Started as a scaffold early on so security thinking wasn't bolted
> on at the end; the STRIDE table below reflects what actually shipped, not a plan.

## Non-goals (explicit)

- No real KYC/AML — customers are self-registered, no identity verification.
- Not PCI-DSS certified — this is not a system for handling real card data.
- Play money only — no integration with real payment rails or real currency.
- No production-grade fraud detection beyond what's described here.

This is an educational project demonstrating security-conscious engineering practice,
not a certified financial system.

## Assets

- Customer PII (name, email, contact details).
- Credentials (password hashes, MFA secrets, recovery codes).
- Account balances and transaction history.
- Session material (access tokens, refresh tokens).

## Trust boundaries

```
Browser  |  BFF (NestJS)  |  Backend (Spring Boot)  |  PostgreSQL
```

The BFF is a new trust boundary the browser's own compromise no longer bounds. Before
Phase 8, a stolen access token was scoped to one user for up to 10 minutes (ADR 0004). A
compromised **BFF process** now holds live refresh tokens for every logged-in user at
once — a materially larger blast radius than any single compromised browser tab. See
[ADR 0005](adr/0005-nestjs-bff-session-cookie.md) for the full reasoning and the
accepted tradeoffs (no Redis/shared session store, `SameSite`/cookie-domain scope).

**Updated in Phase 9 (Render migration)**: the backend's boundary with the BFF is no
longer network-level isolation. On Fly.io the backend was only reachable from the bff
over a private `.internal` network; Render's free plan has no private networking, so
`fernbank-api.onrender.com` is a plain public HTTPS URL anyone can call directly, not
just the bff. Every actual data endpoint is unaffected by this — JWT auth and
server-side ownership checks (`AccountOwnershipGuard`) gate every resource lookup
regardless of caller, so a direct caller gets exactly the same authorization surface a
real client would. The one place this mattered concretely: `LoginRateLimiter`'s
per-(IP, email) bucket used the raw TCP peer address, which is always the bff's own
container IP for a bff-proxied call (a fresh outbound connection, not a shared one) —
collapsing every real user's login attempts onto one shared bucket per email. Fixed via
a shared `INTERNAL_SERVICE_KEY` secret between bff and backend (see the STRIDE table
below) rather than trusting `X-Forwarded-For` unconditionally, since an unconditional
trust would let any direct caller of the backend's public URL forge a fresh IP per
request and bypass the login lockout entirely.

## STRIDE

| Flow | Threat category | Threat | Mitigation | Status |
|---|---|---|---|---|
| Login | Spoofing | Credential stuffing / brute force | `LoginRateLimiter` — Bucket4j token bucket, 5 attempts / 15 min per (IP, email); every attempt (success or failure) consumes a token | Done — Phase 2 |
| Login | Information disclosure | User enumeration via distinct error messages | Uniform `401 Invalid credentials` for bad email vs. bad password | Done — Phase 2 |
| Session | Tampering | JWT signature forgery | RSA-signed tokens, JWKS-based verification (`/.well-known/jwks.json`) | Done — Phase 2 |
| Session | Elevation of privilege | Refresh token replay after theft | Rotating refresh tokens, reuse detection revokes the whole token family | Done — Phase 2 |
| Transfer | Repudiation | Missing audit trail for money movement | `AuditEvent` written for transfers, payee changes, role changes | Done — Phase 2–3 |
| Transfer | Tampering | Double-submit / duplicate transfer | `Idempotency-Key` required on all mutations; same key + different body is `409` | Done — Phase 3 |
| Transfer | Elevation of privilege | High-value transfer without fresh auth | Step-up MFA (`POST /auth/step-up`) required above a configurable threshold (`STEP_UP_THRESHOLD_MINOR_UNITS`) | Done — Phase 2–3 |
| Scheduled transfer | Elevation of privilege | A scheduled transfer executes later, at or above the step-up threshold, with no fresh MFA at execution time | Not mitigated — step-up is checked only on `POST /transfers` at request time, never on the batch job that later executes a `ScheduledTransfer`. Balance is checked (best-effort, not a hold) at scheduling time only. Accepted for v1's scope; documented in `TransferController`'s own Javadoc, not a silent gap | Accepted risk — Phase 4 |
| Account access | Information disclosure | IDOR — client-supplied accountId used without ownership check | Server-side `AccountOwnershipGuard` on every lookup; cross-customer access returns `404`, never `403` | Done — Phase 2–4 |
| Balance | Tampering | Concurrent transfers corrupt balance | Optimistic locking (`@Version`) + retry, concurrency-tested with real parallel transfer requests | Done — Phase 3 |
| API | Denial of service | Unbounded request rate | Bucket4j rate limiting on auth; bff's `@nestjs/throttler` (120 req/60s, keyed by session or IP) as a coarser layer in front of every route | Done — Phase 2, 8 |
| Registration | Information disclosure | Email enumeration — `POST /auth/register` returns `409` for an already-registered email vs. `201` for a new one, letting an attacker test whether an email has an account | Not mitigated — a uniform-response design (e.g. always `201`, confirm by email) was scoped out as unneeded ceremony for a project with no real email-based account recovery flow | Accepted risk — Phase 2 |
| MFA secret | Information disclosure | TOTP secret exposure from a database dump | `MfaSecretConverter` — AES-256-GCM at rest, key from `MFA_SECRET_ENCRYPTION_KEY`, random IV per value | Done — Phase 2 |
| MFA recovery codes | Information disclosure | Recovery code exposure from a database dump lets an attacker bypass MFA | Stored as `PasswordEncoder` hashes (bcrypt), never plaintext; single-use, deleted on redemption | Done — Phase 2 |
| BFF session | Information disclosure | Session cookie theft (XSS, network capture) grants the attacker the victim's refresh token via the BFF | httpOnly (JS can't read it), AES-256-GCM encrypted at rest in the cookie, `Secure` in production; the CSRF cookie is deliberately the only non-httpOnly one | Done — Phase 8 |
| BFF session | Elevation of privilege | CSRF: a third-party site rides the browser's ambient session cookie to trigger a mutation | Double-submit CSRF token, required on every non-GET request once a session exists | Done — Phase 8 |
| BFF | Spoofing (confused deputy) | The BFF process compromised or tricked into acting as any logged-in user against the backend | The BFF is the sole trusted caller from the backend's perspective — a compromised BFF is a materially bigger blast radius than one browser tab; no additional backend-side mitigation exists beyond process isolation. Documented as an accepted, not-yet-hardened tradeoff — see ADR 0005 | Accepted risk — Phase 8 |
| BFF refresh | Elevation of privilege | Concurrent requests for one session both rotate the same refresh token, tripping the backend's reuse-detection and revoking the whole family (denial of service against the legitimate user, not a real compromise) | Per-session single-flight lock around every refresh call, verified under real concurrent HTTP requests | Done — Phase 8 |
| Login | Spoofing | Rate-limit bypass via a forged client IP — once the backend's public URL was directly reachable (Phase 9's move to Render, no private networking), a caller could set an arbitrary `X-Forwarded-For` on a direct call and dodge `LoginRateLimiter`'s per-(IP, email) lockout on every request | Backend only trusts an incoming `X-Forwarded-For` when it's paired with a shared `X-Internal-Service-Key` header matching `INTERNAL_SERVICE_KEY` (same secret, set on both Render services) — trusting the header unconditionally was considered and rejected for exactly this reason | Done — Phase 9 |
| Login | Denial of service | Same bug from the other direction: without the fix above, every login proxied through the bff shared **one** rate-limit bucket per email (the backend saw the bff's own container IP, never the real caller's), so automated traffic against one account could lock out every other legitimate user of that account | Same `INTERNAL_SERVICE_KEY` fix — the backend now rate-limits on the bff-forwarded real client IP instead of the bff's own address | Done — Phase 9 |

## Out of scope for this table (tracked in [ADR 0001](adr/0001-record-architecture-decisions.md) once decided)

- Infrastructure-level threats (cloud provider IAM, network segmentation) — this is a
  portfolio deployment, not a hardened production environment.
- Horizontal scaling of the BFF (shared session/token-cache store, e.g. Redis) — single
  in-memory process only today, see ADR 0005.
- The public live demo (see the README) is a shared, publicly-writable sandbox by
  design — anyone can register their own account or use the shared demo login and move
  play money around. This is accepted, not a vulnerability: no real value exists to
  protect, and it's exactly the "hostile review" audience this project is built to
  withstand at the request/authorization level, not to hide from entirely.
