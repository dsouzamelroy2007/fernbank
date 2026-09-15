# Troubleshooting

Operational incidents worth a permanent record — not architecture decisions (see
`docs/adr/`), just hard-won debugging context that would otherwise be lost.

## Render free-tier cold start: the bff's own wake-up requests get rate-limited

**Symptom**: after ~15 minutes idle, Render's free tier sleeps both `fernbank-api` and
`fernbank-bff`. A real browser visiting the app was expected to wake both automatically
(any inbound request wakes a sleeping Render service) and log in normally once ready.
Instead, the bff would wake, but the backend never came up — its logs showed nothing at
all, sometimes for 6+ minutes.

**Root cause, as far as it's understood**: the bff's own outbound calls to the
backend's public health URL were being rejected by Render's edge with:

```
429 Too Many Requests
x-render-routing: hibernate-rate-limited
```

confirmed (via Render support) to be decided entirely at Render's edge, before the
request ever reaches the backend app or triggers a resume-from-hibernation event.
Render never identified *why* the bff's requests specifically were flagged. The
strongest evidence: a direct request from a real browser to the same URL consistently
woke the backend with no block, in the same test session where the bff's own request
failed — pointing at the bff's Render-assigned origin itself being rate-limited, not
the URL, and not anything about request timing or headers.

**What was tried and ruled out** (each confirmed live, none of it changed the outcome):

- Slowing the poll interval from 4s to 25s.
- Dropping automatic retries entirely — a single attempt, spaced minutes to a full day
  apart in testing, with a manual "Try again" affordance instead of a loop.
- A completely fresh bff process's very first attempt after a redeploy (ruling out
  "recent request history from this process" as the trigger).
- Sending a browser-like `User-Agent`/`Accept` header on the bff's outbound call
  (ruling out simple bot-fingerprint heuristics on that specific request).
- Switching Render's own `healthCheckPath` off a DB-dependent endpoint
  (`/actuator/health` → `/actuator/health/liveness`), in case Neon's own independent
  cold-start was making Render's internal health verification flaky on wake and
  triggering some kind of backoff. Deployed, confirmed live, no change after 24+ hours.
- Confirmed not a quota issue: Render's own usage page showed ~37/750 free instance
  hours used for the month at the time.

**The fix that actually worked** (2026-09-12) — a sidestep, not a resolution of the
root cause: the frontend now fires a direct, fire-and-forget wake-up request from the
**browser itself** to the backend's public health URL, in `no-cors` mode, alongside the
existing bff-routed readiness check. Since a browser-originated request to that URL
isn't blocked, this reaches Render from a path that's actually proven to work, while
the bff's own check still determines real readiness (does the backend, and Neon,
actually respond `UP`) once it's awake. See `NEXT_PUBLIC_BACKEND_HEALTH_URL` in
`infra/.env.example` and `pingBackendDirectly()` in `frontend/src/lib/api/warmup.ts`.

Verified live against a genuinely cold stack (both services slept, no manual
pre-warming): visiting the Vercel URL normally now wakes the backend and completes
login successfully.

**If this resurfaces**: don't re-try timing or header variations on the bff's own
call — several were already tried above and didn't help. The one thing that reliably
worked was originating the wake-up request from outside Render's infrastructure
entirely.
