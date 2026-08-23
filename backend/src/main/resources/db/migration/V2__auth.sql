-- Auth: roles, MFA, and account-lockout state on top of the Phase 1 schema.

ALTER TABLE app_user
    ADD COLUMN mfa_secret            varchar(255),
    ADD COLUMN mfa_enabled           boolean NOT NULL DEFAULT false,
    ADD COLUMN failed_login_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN locked_until          timestamptz;

CREATE TABLE user_role (
    user_id uuid        NOT NULL,
    role    varchar(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_user_role_role CHECK (role IN ('CUSTOMER', 'SUPPORT', 'ADMIN'))
);

CREATE TABLE recovery_code (
    id         uuid PRIMARY KEY,
    user_id    uuid         NOT NULL,
    code_hash  varchar(255) NOT NULL,
    used_at    timestamptz,
    created_at timestamptz  NOT NULL,
    CONSTRAINT fk_recovery_code_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX ix_recovery_code_user_id ON recovery_code (user_id);
