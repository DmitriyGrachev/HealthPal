-- Run immediately before V21 while writers are stopped.
LOCK TABLE durable_jobs IN SHARE ROW EXCLUSIVE MODE;

WITH RECURSIVE candidates (id, base_key, suffix, candidate_key) AS (
    SELECT job.id,
           'legacy:' || job.id::text,
           0,
           'legacy:' || job.id::text
      FROM durable_jobs job
     WHERE job.idempotency_key IS NULL
    UNION ALL
    SELECT candidate.id,
           candidate.base_key,
           candidate.suffix + 1,
           candidate.base_key || ':' || (candidate.suffix + 1)::text
      FROM candidates candidate
     WHERE EXISTS (
               SELECT 1
                 FROM durable_jobs existing
                WHERE existing.idempotency_key = candidate.candidate_key
           )
), chosen AS (
    SELECT DISTINCT ON (id) id, candidate_key
      FROM candidates candidate
     WHERE NOT EXISTS (
               SELECT 1
                 FROM durable_jobs existing
                WHERE existing.idempotency_key = candidate.candidate_key
           )
     ORDER BY id, suffix
)
UPDATE durable_jobs job
   SET idempotency_key = chosen.candidate_key
  FROM chosen
 WHERE job.id = chosen.id
   AND job.idempotency_key IS NULL;
