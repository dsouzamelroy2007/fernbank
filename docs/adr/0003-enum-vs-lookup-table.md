# 3. Enums, not lookup tables, for account/user classification fields

## Status

Accepted

## Context

`Account.type` (`CHECKING`/`SAVINGS`), `Account.status` (`ACTIVE`/`FROZEN`/`CLOSED`), and
`User.status` (`ACTIVE`/`LOCKED`/`DISABLED`) each need a fixed set of values. Three ways
to model this in Postgres + JPA: a foreign-key lookup table, a native Postgres `ENUM`
type, or a `varchar` column constrained by a `CHECK`.

## Decision

Use Java enums (`@Enumerated(EnumType.STRING)`) backed by a `varchar` column with a
`CHECK (... IN (...))` constraint.

- **Not a lookup table**: these values aren't admin-editable business data (nobody adds
  a new account type through an admin UI) — they're closed sets tied 1:1 to code
  branches. A lookup table would add a join to every account/user read for no benefit:
  no extra metadata (display name, sort order) is needed, and the values can't change
  without a code deploy anyway.
- **Not a native Postgres `ENUM` type**: adding a value to a native enum
  (`ALTER TYPE ... ADD VALUE`) can't run inside the same transaction as other DDL/DML in
  older Postgres versions and complicates repeatable Flyway migrations for something
  that a plain `CHECK` constraint change handles trivially.
- **`varchar` + `CHECK`**: readable in the DB without decoding an enum OID, trivially
  extended by a new Flyway migration (`ALTER TABLE ... DROP CONSTRAINT ...`, `ADD
  CONSTRAINT ... CHECK (...)`), and matches Hibernate's `EnumType.STRING` mapping
  exactly, so `ddl-auto: validate` has a straightforward column type to check.

## Consequences

- Adding a new value (e.g. a `BUSINESS` account type) is a two-part change: extend the
  Java enum, then a new Flyway migration that redefines the `CHECK` constraint. There is
  no way to add a value without a deploy.
- The `CHECK` constraint is the actual enforcement boundary — the Java enum alone
  doesn't stop a raw SQL `INSERT` from writing an invalid value.
