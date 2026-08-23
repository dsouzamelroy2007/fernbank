# 4. JWT + rotating refresh tokens, not server-side sessions

## Status

Accepted

## Context

The client is a Next.js frontend talking to this API over the browser. Two realistic
auth models: server-side sessions (opaque session id in an httpOnly cookie, session
state in the DB) or JWT access tokens + refresh tokens.

Sessions are the safer default for a browser-only client: revocation is immediate and
trivial (delete the session row), there's no token to steal from `localStorage`, and
there's less cryptographic machinery to get wrong. This project chose JWT anyway,
deliberately, to exercise more of Spring Security's resource-server/JWT surface — that
tradeoff is stated here rather than left implicit.

## Decision

- Short-lived (10 min) RSA-signed JWT access tokens, verified statelessly by the
  resource server on every request — no DB lookup per request.
- Opaque, rotating refresh tokens, stored only as a SHA-256 hash, with reuse detection:
  presenting an already-rotated refresh token revokes its entire family. This is the
  mechanism that makes JWT's biggest weakness (can't be revoked mid-flight) tolerable —
  the access-token blast radius from a stolen token is capped at 10 minutes, and a
  stolen *refresh* token is caught the first time the legitimate client's next rotation
  collides with the attacker's.
- MFA challenge and step-up-elevation are additional narrow-purpose JWTs
  (`token_use=mfa_challenge` / an `elevated_until` claim on an access token) rather than
  server-side state, staying consistent with the stateless design.
- The frontend (Phase 6) must not put the access token in `localStorage` — this ADR
  doesn't force cookies, but the non-negotiable in `CONTRIBUTING.md` still applies regardless
  of the auth model.

## Consequences

- No "log out everywhere" for a *live* access token short of waiting out its 10-minute
  TTL — only refresh-token issuance is revocable immediately. Considered acceptable
  given the short TTL; would need to change if TTLs grew.
- Reuse detection only protects the refresh-token chain, not a leaked access token in
  its remaining lifetime. Mitigated by the short TTL and, on top of that, step-up
  requiring a fresh MFA assertion for high-value actions (Phase 3) regardless of how the
  access token was obtained.
- If a future BFF (Phase 8, optional) puts sessions back in front of this API, this ADR
  is superseded, not edited — a new ADR would record that reversal and why.
