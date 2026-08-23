## What and why

<!-- One or two sentences: what changed, and the reason - not just a restatement of the diff. -->

## Which module(s)

- [ ] Backend (Java / Spring Boot)
- [ ] BFF (NestJS)
- [ ] Frontend (Next.js)
- [ ] Infra (Docker Compose / Render / GitHub Actions)
- [ ] Docs

## Checklist

- [ ] Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `chore:`, `test:`, `docs:`, ...)
- [ ] Tests added or updated for the behavior changed (see `CONTRIBUTING.md`'s Testing section - domain logic gets plain unit tests, anything touching real SQL gets a Testcontainers integration test, security-sensitive changes get a regression test alongside the happy path)
- [ ] If this touches money movement: a concurrency test exists, not just a happy-path test
- [ ] If this touches auth, ownership checks, or any other security-sensitive path: covered by a regression test (expired token, tampered signature, wrong role, IDOR attempt) per `CONTRIBUTING.md`
- [ ] No new `float`/`double` anywhere near a monetary amount
- [ ] No secrets committed (checked `git diff` for stray `.env` values, keys, tokens)
- [ ] Docs updated if this changes behavior described in `README.md`, `docs/architecture.md`, or `docs/threat-model.md`

## How was this tested?

<!-- Commands you ran, or what you exercised manually against the local Docker Compose stack / the live demo. -->
