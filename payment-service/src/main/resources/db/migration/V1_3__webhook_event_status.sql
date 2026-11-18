-- A webhook is claimed before domain handling so concurrent retries cannot both
-- process it. The row is marked PROCESSED only after handling succeeds; a failed
-- attempt becomes FAILED and can be reclaimed when the provider redelivers.
ALTER TABLE processed_webhook_events
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PROCESSED';

UPDATE processed_webhook_events SET status = 'PROCESSED' WHERE status IS NULL;
