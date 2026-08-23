-- Core domain schema: customers, auth identities, accounts, and the double-entry ledger.

CREATE TABLE customer (
    id         uuid PRIMARY KEY,
    full_name  varchar(255) NOT NULL,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL
);

CREATE TABLE app_user (
    id            uuid PRIMARY KEY,
    customer_id   uuid         NOT NULL,
    email         varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    status        varchar(20)  NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT uk_app_user_customer_id UNIQUE (customer_id),
    CONSTRAINT uk_app_user_email UNIQUE (email),
    CONSTRAINT fk_app_user_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE TABLE account (
    id             uuid PRIMARY KEY,
    customer_id    uuid         NOT NULL,
    account_number varchar(34)  NOT NULL,
    type           varchar(20)  NOT NULL,
    currency       varchar(3)   NOT NULL,
    status         varchar(20)  NOT NULL,
    version        bigint       NOT NULL DEFAULT 0,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    CONSTRAINT uk_account_account_number UNIQUE (account_number),
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT ck_account_type CHECK (type IN ('CHECKING', 'SAVINGS')),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE INDEX ix_account_customer_id ON account (customer_id);

CREATE TABLE account_balance (
    account_id           uuid PRIMARY KEY,
    balance_minor_units  bigint      NOT NULL,
    currency             varchar(3)  NOT NULL,
    version              bigint      NOT NULL DEFAULT 0,
    updated_at           timestamptz NOT NULL,
    CONSTRAINT fk_account_balance_account FOREIGN KEY (account_id) REFERENCES account (id)
);

CREATE TABLE transaction (
    id                 uuid PRIMARY KEY,
    description        varchar(255),
    created_by_user_id uuid,
    created_at         timestamptz NOT NULL,
    CONSTRAINT fk_transaction_created_by_user FOREIGN KEY (created_by_user_id) REFERENCES app_user (id)
);

CREATE TABLE ledger_entry (
    id                 uuid PRIMARY KEY,
    transaction_id     uuid        NOT NULL,
    account_id         uuid        NOT NULL,
    amount_minor_units bigint      NOT NULL,
    currency           varchar(3)  NOT NULL,
    created_at         timestamptz NOT NULL,
    CONSTRAINT fk_ledger_entry_transaction FOREIGN KEY (transaction_id) REFERENCES transaction (id),
    CONSTRAINT fk_ledger_entry_account FOREIGN KEY (account_id) REFERENCES account (id)
);

CREATE INDEX ix_ledger_entry_account_id_created_at ON ledger_entry (account_id, created_at);
CREATE INDEX ix_ledger_entry_transaction_id ON ledger_entry (transaction_id);

CREATE TABLE payee (
    id                     uuid PRIMARY KEY,
    customer_id            uuid         NOT NULL,
    name                   varchar(255) NOT NULL,
    target_account_number  varchar(34)  NOT NULL,
    created_at             timestamptz  NOT NULL,
    CONSTRAINT fk_payee_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);

CREATE INDEX ix_payee_customer_id ON payee (customer_id);

CREATE TABLE idempotency_record (
    id               uuid PRIMARY KEY,
    user_id          uuid         NOT NULL,
    idempotency_key  varchar(255) NOT NULL,
    request_hash     varchar(64)  NOT NULL,
    response_status  integer,
    response_body    jsonb,
    created_at       timestamptz  NOT NULL,
    expires_at       timestamptz  NOT NULL,
    CONSTRAINT uk_idempotency_record_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_idempotency_record_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE audit_event (
    id            uuid PRIMARY KEY,
    actor_user_id uuid,
    event_type    varchar(255) NOT NULL,
    metadata      jsonb,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT fk_audit_event_actor_user FOREIGN KEY (actor_user_id) REFERENCES app_user (id)
);

CREATE INDEX ix_audit_event_actor_user_id ON audit_event (actor_user_id);

CREATE TABLE refresh_token (
    id                    uuid PRIMARY KEY,
    user_id               uuid        NOT NULL,
    token_hash            varchar(255) NOT NULL,
    family_id             uuid        NOT NULL,
    issued_at             timestamptz NOT NULL,
    expires_at            timestamptz NOT NULL,
    revoked_at            timestamptz,
    replaced_by_token_id  uuid,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX ix_refresh_token_user_id ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_family_id ON refresh_token (family_id);

-- Ledger entries are append-only: reject any UPDATE or DELETE outright.
CREATE FUNCTION prevent_ledger_entry_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entry rows are append-only and cannot be updated or deleted (id=%)',
        COALESCE(OLD.id, NEW.id);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entry_immutable
    BEFORE UPDATE OR DELETE ON ledger_entry
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_entry_mutation();

-- Double-entry invariant: every transaction's ledger entries must have at least two
-- rows, sum to zero, and share a single currency. Deferred to COMMIT so the app can
-- insert each entry independently within one DB transaction.
CREATE FUNCTION check_transaction_balances() RETURNS trigger AS $$
DECLARE
    entry_count   integer;
    entry_sum     bigint;
    currency_count integer;
    affected_transaction_id uuid;
BEGIN
    affected_transaction_id := COALESCE(NEW.transaction_id, OLD.transaction_id);

    SELECT count(*), COALESCE(sum(amount_minor_units), 0), count(DISTINCT currency)
    INTO entry_count, entry_sum, currency_count
    FROM ledger_entry
    WHERE transaction_id = affected_transaction_id;

    IF entry_count < 2 THEN
        RAISE EXCEPTION 'transaction % has fewer than 2 ledger entries (got %)',
            affected_transaction_id, entry_count;
    END IF;

    IF entry_sum <> 0 THEN
        RAISE EXCEPTION 'transaction % ledger entries do not sum to zero (got %)',
            affected_transaction_id, entry_sum;
    END IF;

    IF currency_count <> 1 THEN
        RAISE EXCEPTION 'transaction % ledger entries span more than one currency',
            affected_transaction_id;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_entry_balance
    AFTER INSERT ON ledger_entry
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_transaction_balances();
