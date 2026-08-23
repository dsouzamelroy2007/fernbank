# 6. Double-entry, append-only ledger instead of a mutable balance column

## Status

Accepted

## Context

Every account needs a balance. The simplest model is a single mutable `balance` column
on `Account`, updated in place by each deposit/withdrawal/transfer. That's enough to
answer "what's the balance right now," but not "why is it what it is" or "prove this
number is correct" — both routine requirements for anything calling itself a ledger,
and exactly the kind of property a hostile reviewer of a banking-style project would
probe first.

## Decision

Model every balance change as a `Transaction` containing two or more append-only
`LedgerEntry` rows whose signed amounts sum to zero (a debit and a credit, or more for
a multi-leg operation). `LedgerEntry` rows are never updated or deleted after insert —
a correction is a new offsetting entry, not an edit to history. An account's balance is
derived by summing its `LedgerEntry` rows, not stored and mutated directly.

- **Correctness by construction, not convention.** With a mutable `balance` column, an
  unexplained balance change is only *unlikely* if every code path remembers to log it
  correctly. With double-entry, an unexplained change is *structurally impossible* — the
  entries either sum to zero and reconcile, or they don't exist. Reconciliation becomes
  "do the entries sum to zero," not "trust the number."
- **Append-only gives a real audit trail for free.** Every historical balance is
  reconstructable by replaying entries up to a point in time, with no separate
  audit-log table to keep in sync with the source of truth.
- **The cost is real, and accepted.** This is more ceremony than one `UPDATE accounts
  SET balance = ...` — every money movement writes multiple rows inside one transaction,
  and reading a balance means an aggregate query (or a maintained projection) instead of
  a column read. For this project's scale, correctness and auditability are worth more
  than the extra write/read cost.

## Consequences

- Balance reads need either an aggregation query over `LedgerEntry` or a maintained
  summary (this project uses the former — see the `banking` package). A much
  higher-throughput system would eventually want a cached/materialized balance kept in
  sync with the ledger, not a change to the ledger model itself.
- Every money-movement code path must produce balanced entries inside one DB
  transaction — a partial write (one leg committed, the other not) would violate the
  core invariant. Optimistic locking (`@Version`) plus concurrency tests exist
  specifically to catch this under concurrent writers (see `CONTRIBUTING.md`'s
  Concurrency safety rule).
- There is no `UPDATE`/`DELETE` path for `LedgerEntry` anywhere in the codebase by
  design — a correction is always a new offsetting `Transaction`, never a retroactive
  edit.
