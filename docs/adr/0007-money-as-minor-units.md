# 7. Money as BIGINT minor units + ISO-4217 currency code, never a float

## Status

Accepted

## Context

Every amount in the system - balances, ledger entries, transfer requests, API
responses - needs a representation. `float`/`double` is the obvious default in most
languages and is exactly wrong for money: binary floating point cannot represent most
decimal fractions exactly (`0.1 + 0.2 != 0.3`), so repeated arithmetic on a float
balance drifts. `BigDecimal`/`java.math` arithmetic in the domain avoids the drift but
still leaves an open question at the API boundary: a JSON number is parsed by clients
(this project's own frontend included) through JavaScript's `number` type, which is
IEEE-754 double-precision - so even a perfectly precise server-side decimal becomes
imprecise the moment it round-trips through a browser as a raw JSON number.

## Decision

Store every amount as a `BIGINT` in the currency's minor unit (cents for USD/EUR, not
major units) alongside an ISO-4217 currency code, wrapped in a single `Money` value
object that owns all arithmetic. Expose amounts over the API as a `{ amount: "125.50",
currency: "USD" }` shape - a decimal **string**, not a raw JSON number - so no client
language's numeric type ever touches the value in a lossy way.

- **`BIGINT` minor units, not `BigDecimal`/`NUMERIC` major units.** Integer arithmetic
  has no rounding-mode ambiguity and no representation error - `4990` cents is exact,
  full stop. All arithmetic (add, subtract, compare, sum-to-zero validation) goes
  through `Money`; there is no ad-hoc `long` math on amounts anywhere else in the
  codebase, per this project's non-negotiables (see `CONTRIBUTING.md`).
- **String, not number, on the wire.** A JSON number is the one part of this decision
  a backend-only choice can't fully protect: even flawless server-side integer math is
  undone the instant a client parses `125.50` as a JS `number` and does further math on
  it. A decimal string forces every client to explicitly parse it with a
  decimal-aware library instead of accidentally inheriting float semantics for free.
- **Currency code travels with every amount.** A bare integer is meaningless without
  knowing both the currency and its minor-unit scale (cents for USD, no minor unit at
  all for JPY) - `Money` always carries both together, so "125.50 USD" is never
  separated from its currency partway through a call chain.

## Consequences

- Same-currency-only for v1: every arithmetic operation between two `Money` values
  requires matching currency codes, since minor-unit scale and conversion rates aren't
  handled by this value object. Cross-currency transfers are out of scope (see the
  README's Roadmap) rather than a partially-correct implementation.
- Every new endpoint or DTO carrying a monetary amount must use the same
  `{amount, currency}` string shape - a raw numeric field anywhere near money is a
  regression against this decision, not a stylistic nitpick.
- Frontend and bff code parses these decimal strings explicitly (never
  `Number(amount)` followed by further arithmetic) before formatting for display.
