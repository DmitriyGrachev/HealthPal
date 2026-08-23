-- Spring Modulith 2.x tracks an explicit publication lifecycle. Existing
-- incomplete rows represent abandoned pre-upgrade delivery attempts and must
-- be eligible for the failed-publication resubmission API after the upgrade.
ALTER TABLE event_publication
    ADD COLUMN status TEXT,
    ADD COLUMN completion_attempts INT,
    ADD COLUMN last_resubmission_date TIMESTAMP WITH TIME ZONE;

UPDATE event_publication
   SET status = CASE
           WHEN completion_date IS NULL THEN 'FAILED'
           ELSE 'COMPLETED'
       END,
       completion_attempts = 1;

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON event_publication USING hash(serialized_event);
