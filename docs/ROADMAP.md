# Roadmap

What's deliberately *not* here today, and why — each of these was considered and
scoped out on purpose, not overlooked. Reopening one means arguing the reasoning below
no longer holds, not just adding the feature.

- **Cards, card issuing, or payment-scheme (Visa/Mastercard) integration.** Real card
  issuing and scheme membership is regulated, expensive, and enormous in scope —
  disproportionate for a portfolio/educational project. If this project ever grows a
  cards feature, it belongs as its own self-contained addition (issuing, tokenization,
  a scheme sandbox integration), not a bolt-on to the current internal-ledger scope.
- **Distributed tracing** (Micrometer Tracing + OpenTelemetry + Tempo/Zipkin/Jaeger) —
  correlation-id-linked structured logs cover this project's actual debugging needs;
  full tracing is a real infra decision to make only if it's ever actually needed.
- **Hosted Prometheus/Grafana/Loki in production** — local-only via
  `docker compose --profile observability up`; Render's own logs/metrics are the
  production story at this project's scale.
- **A true fund hold/reservation on scheduled transfers** — today's check at schedule
  time is best-effort (validates the balance, doesn't lock it), documented as such in
  [docs/architecture.md](architecture.md). An actual reservation needs a
  reserved-vs-available-balance concept, which is a bigger change than this project has
  needed yet.
- **Cross-currency transfers.** `Money` is same-currency-only by design (see
  [ADR 0007](adr/0007-money-as-minor-units.md)) — adding FX means real exchange-rate
  sourcing, rounding rules, and a decision about who bears rate risk, which is out of
  scope for an internal ledger.
- **Horizontal scaling of the BFF** (a shared session/token-cache store, e.g. Redis) —
  single in-memory process only today; see [ADR 0005](adr/0005-nestjs-bff-session-cookie.md)
  for the accepted tradeoff and what would need to change.

## Ideas worth revisiting, not yet scoped

Unlike the list above, these don't have a settled "no" — just not built yet:

- Real KYC/AML and identity verification, if this project ever needed to look less like
  a portfolio piece and more like a real product (see the non-goals list in
  [docs/threat-model.md](threat-model.md) for why they're absent today).
- A password-reset / account-recovery email flow — today's MailHog integration is
  dev-only tooling, not a real transactional-email path.
- Multi-instance backend deployment — `LoginRateLimiter` is explicitly single-instance
  today (in-memory Bucket4j buckets); a distributed deployment would need a shared
  store (e.g. Redis-backed `ProxyManager`) first.
