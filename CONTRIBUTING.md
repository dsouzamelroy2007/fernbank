# Contributing to fernbank

This is a portfolio/educational project first, and open to outside contributions
second — but genuine fixes, tests, and well-scoped features are welcome. Read the
non-negotiables below before you start; they're the actual rules this codebase is held
to, not just a suggestion.

## Non-negotiables

- **Money is never `float`/`double`.** Store as `BIGINT` minor units + ISO-4217 currency
  code. Expose over the API as a string amount + currency code, never a raw number that
  could round-trip through JS floating point. All arithmetic goes through a `Money`
  value object; no ad-hoc `long` math on amounts outside it.
- **Double-entry, append-only ledger.** Every balance change is a `Transaction`
  containing two or more `LedgerEntry` rows whose signed amounts sum to zero.
  `LedgerEntry` rows are never updated or deleted after insert — corrections are new
  offsetting entries.
- **Idempotency.** Every write endpoint (`POST`/`PUT`/`PATCH` that mutates state)
  requires an `Idempotency-Key` header. Same key + same body replays the original
  response; same key + different body is a `409`.
- **Ownership checks are server-side.** Never trust a client-supplied
  `accountId`/`userId` to imply access. Every resource lookup must verify the
  authenticated principal owns (or is authorized for) the resource. Cross-customer
  access returns `404`, not `403` — don't leak existence.
- **No secrets in code.** All configuration (DB creds, JWT signing keys, mail creds)
  comes from environment variables. `.env.example` is committed; `.env` is gitignored.
- **Concurrency safety.** Balance mutations must be safe under concurrent writers
  (optimistic locking via `@Version` and/or row locking). Any change touching money
  movement needs a concurrency test, not just a happy-path test.
- **No sensitive data in logs.** Never log tokens (access, refresh, MFA secrets,
  recovery codes), passwords, full account numbers, or an amount together with the
  identity it belongs to.

## Before you open a PR

- **Check the non-goals first.** The README's Roadmap section and
  `docs/threat-model.md`'s non-goals list name things that are deliberately out of
  scope (cards/scheme integration, distributed tracing, hosted production
  observability, a true fund-reservation on scheduled transfers) with the reasoning
  given inline. A PR reopening one of these needs to argue why the reasoning no longer
  holds, not just add the feature.
- **One concern per PR.** Small, focused changes are much easier to review than one
  PR touching backend, frontend, and infra at once.
- **Ask before adding a dependency.** Same rule this project holds its own work to.
- **Don't add features, scope, or abstractions beyond what's asked** (no cards, loans,
  FX, crypto — see `/docs/adr` for the reasoning if it needs re-litigating).

## Local setup

```bash
cp infra/.env.example infra/.env
cd infra
docker compose --env-file .env up --build
```

See the README's [Local setup](README.md#local-setup) section for ports, demo login,
and running each service individually during development.

## Testing

```bash
cd backend && ./gradlew test     # unit + Testcontainers integration tests
cd bff && npm test && npm run test:e2e
cd frontend && npm run lint && npm run typecheck && npm run build
```

`./gradlew check` additionally runs ArchUnit layering rules, a JaCoCo coverage gate,
Checkstyle, and OWASP dependency-check.

Match the existing testing shape, not just "add a test":

- Domain logic (`Money`, ledger invariants, use cases) — plain unit tests, no Spring
  context.
- Anything touching a repository or controller — a Testcontainers PostgreSQL
  integration test. No H2, no mocked DB for anything that touches real SQL.
- Anything touching balance mutation — a concurrency test with real parallel requests,
  not just a happy-path test.
- Anything security-sensitive (auth, ownership checks, token handling) — a regression
  test alongside the happy path (expired token, tampered signature, wrong role, IDOR
  attempt).

## Commit style

[Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`,
`chore:`, `test:`, `docs:`, ...), small commits, one concern per commit.

## Opening a PR

The PR template will ask you to confirm the checklist above. CI (`backend-ci`,
`bff-ci`, `frontend-ci`, `security`) runs automatically on every PR — it needs to be
green before review.

## Reporting a security issue

Please use [GitHub Security Advisories](https://github.com/dsouzamelroy2007/fernbank/security/advisories/new)
for anything you think is a genuine vulnerability, rather than a public issue — even
though this is a play-money educational project with no real funds or PII at stake,
it's good practice and lets a fix land before the report is public.
