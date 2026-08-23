CREATE TABLE scheduled_transfer (
    id                       uuid PRIMARY KEY,
    source_account_id       uuid         NOT NULL,
    destination_account_id  uuid         NOT NULL,
    amount_minor_units       bigint       NOT NULL,
    currency                 varchar(3)   NOT NULL,
    description              varchar(255),
    scheduled_for            timestamptz  NOT NULL,
    status                   varchar(20)  NOT NULL,
    created_by_user_id       uuid         NOT NULL,
    created_at               timestamptz  NOT NULL,
    executed_at              timestamptz,
    failure_reason           varchar(255),
    CONSTRAINT fk_scheduled_transfer_source FOREIGN KEY (source_account_id) REFERENCES account (id),
    CONSTRAINT fk_scheduled_transfer_destination FOREIGN KEY (destination_account_id) REFERENCES account (id),
    CONSTRAINT fk_scheduled_transfer_created_by FOREIGN KEY (created_by_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_scheduled_transfer_status CHECK (status IN ('PENDING', 'EXECUTED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX ix_scheduled_transfer_status_scheduled_for ON scheduled_transfer (status, scheduled_for);
