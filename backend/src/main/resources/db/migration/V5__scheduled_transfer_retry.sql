-- Retry support for ScheduledTransferRunner: a failed attempt increments this and
-- leaves status PENDING so the next hourly-ish tick retries it automatically, up to a
-- small fixed attempt limit before the runner marks it terminally FAILED.
ALTER TABLE scheduled_transfer ADD COLUMN attempt_count integer NOT NULL DEFAULT 0;
