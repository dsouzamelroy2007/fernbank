# 5. NestJS BFF: encrypted session cookie, double-submit CSRF, port-insensitive cookie matching in dev

## Status

Accepted. Supersedes [ADR 0004](0004-jwt-vs-server-sessions.md)'s browser-facing
consequences — the backend's own JWT/refresh-token mechanism described there is
unchanged and still accurate for BFF↔backend traffic; only the browser's side of the
model reverses.

## Context

Introducing a BFF was optional scope — worth doing to put Node backend experience in
this portfolio, and flagged upfront as a real architectural reversal to confirm before
starting. It was confirmed: insert a NestJS BFF
between the Next.js frontend and the Spring Boot backend, and the browser must never see
a JWT again.

ADR 0004 accepted a real cost to keep the backend stateless and JWT-pure: no
`localStorage`, so Phase 6 built an in-memory-access-token + httpOnly-refresh-cookie
hybrid, with a single-flighted client-side refresh guard against the backend's
rotate-and-revoke-on-reuse behavior. Phase 8 moves that entire mechanism — and its
single-flight guard — server-side into the BFF, on behalf of every logged-in session at
once instead of one browser tab at a time.

## Decision

- **Session cookie** (`fernbank_bff_session`): httpOnly, AES-256-GCM encrypted with
  Node's built-in `crypto` (no new dependency), payload `{sessionId, refreshToken}`. The
  BFF is the only thing that ever decrypts it. If `BFF_SESSION_ENCRYPTION_KEY` is unset,
  an ephemeral key is generated at startup with a warning — same fallback the backend's
  own `JwtKeys`/`MfaSecretConverter` already use, so `docker compose up` on a fresh clone
  still works with no `.env` at all.
- **CSRF cookie** (`fernbank_bff_csrf`): non-httpOnly, random token, set alongside the
  session cookie. The frontend reads it and echoes it as `X-CSRF-Token` on every
  non-GET request — a standard double-submit pattern, hand-rolled rather than a new
  dependency (matching this codebase's existing precedent of hand-rolling security
  primitives this size, e.g. `TotpService`'s RFC 6238 implementation). It no-ops when no
  session cookie exists yet (login/register aren't ambient-credential-riding targets).
- **`SameSite=Lax`, no explicit cookie `Domain`, by default.** This is deliberately
  *not* airtight for every possible deployment topology, and that gap is written down
  here rather than discovered later:
  - Cookies are scoped by **hostname only** — the port is not part of cookie-domain
    matching (RFC 6265). In this repo's actual dev/Compose topology, frontend and BFF
    are both `localhost`, just different ports, so a host-only cookie set by the BFF
    (`:4000`) is sent correctly on requests to the frontend's own origin (`:3000`/`:3001`)
    with zero extra configuration. `SameSite=Lax` also permits it on top-level
    cross-site navigations regardless.
  - This stops working the moment frontend and BFF sit on genuinely different
    hostnames in a real deployment (e.g. `app.fernbank.com` vs. `bff.fernbank.com`) — a
    cookie set by one is not sent to the other by default. `BFF_SESSION_COOKIE_DOMAIN`
    (optional, unset in dev) exists for exactly this: set it to a shared parent domain
    (`.fernbank.com`) with both frontend and BFF as subdomains, or put one reverse-proxy
    origin in front of both so the browser only ever sees one hostname. Neither is
    configured today — this is the one concrete thing a real deployment of this project
    would need to decide, not a bug to fix now.
  - **Resolved in Phase 9**: the actual deploy target (Vercel for the frontend, Render
    for the bff) landed on genuinely different domains (`*.vercel.app`/`*.onrender.com`),
    with no shared parent domain available. Rather than requiring the user to buy a
    custom domain or weaken the cookie to `SameSite=None`, Phase 9 built the
    reverse-proxy option named above: `frontend/next.config.ts`'s `rewrites()` proxies
    `/api/v1/*` and `/bff/*` to the bff's Render origin at Vercel's edge, so the
    browser only ever sees one hostname and `SameSite=Lax` keeps working exactly as
    designed. See `docs/architecture.md`'s deploy section for the full runbook.
- **Access-token cache, single-flighted per session** (`AccessTokenCacheService`): the
  backend's `POST /auth/refresh` always rotates and revokes-on-reuse — unchanged from
  ADR 0004. Relocating token custody server-side means N concurrent requests for one
  session could now trigger N concurrent refreshes instead of one browser tab's worth;
  the single-flight lock (`Map<sessionId, Promise<TokenPair>>`) prevents that, verified
  under both a direct-call unit test and real concurrent HTTP requests through the full
  Nest/Express stack.
- **Step-up elevation stays server-side too**: a successful `POST /auth/step-up`
  overwrites the session's cached access token with the elevated one rather than
  returning it to the browser — the transfer retry that triggered step-up automatically
  rides it, same behavior as the old `tokenStore.set(accessToken)`, just relocated.

## Consequences

- **Blast radius changed shape, not size.** ADR 0004 accepted that a stolen access
  token is live for up to 10 minutes, scoped to one user. A compromised BFF *process*
  now holds live refresh tokens for every logged-in user at once — a materially larger
  single point of failure than any one compromised browser tab was. This is the
  motivating STRIDE addition in `docs/threat-model.md` for this phase.
- **`LoginRateLimiter`'s IP dimension degrades.** The backend's login rate limiter keys
  on `(ip, email)`; once the BFF is the caller, `ip` is always the BFF's own address,
  not the original browser's. Email keying still bounds per-account brute force, so this
  is accepted, not fixed — restoring it would mean the BFF forwarding a trusted
  `X-Forwarded-For` and the backend adopting a `ForwardedHeaderFilter`, both real changes
  neither side needed until now.
- **No Redis or shared session store.** The access-token cache and the SSE
  notification poller registry are both plain in-memory `Map`s, scoped to one BFF
  process. Fine for this project's actual scale; a horizontally-scaled deployment would
  need to externalize both, which is a real, not-yet-needed piece of future work, not an
  oversight.
- **SSE notifications are BFF-side polling, not a true backend event stream.** The
  backend has no push/event mechanism and adding one was out of scope for this phase —
  `GET /bff/notifications` is a real, long-lived SSE connection to the browser, backed
  by the BFF periodically re-polling each session's accounts on its behalf, ref-counted
  per session so N browser tabs for one user share one poll loop rather than each
  starting their own.
