-- Adds AccountType.SYSTEM for internal CASH_CLEARING counterparty accounts, and seeds
-- the one "System" customer row they belong to (a fixed, well-known id - not achievable
-- via the JPA @UuidGenerator-backed Customer entity, so this is raw SQL bootstrap data).

ALTER TABLE account DROP CONSTRAINT ck_account_type;
ALTER TABLE account ADD CONSTRAINT ck_account_type CHECK (type IN ('CHECKING', 'SAVINGS', 'SYSTEM'));

INSERT INTO customer (id, full_name, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'System', now(), now());

-- At most one CASH_CLEARING (SYSTEM) account per currency; deposit/withdraw create
-- these lazily on first use, so this guards against a race between two lazy-creates.
CREATE UNIQUE INDEX uk_account_system_currency ON account (currency) WHERE type = 'SYSTEM';
