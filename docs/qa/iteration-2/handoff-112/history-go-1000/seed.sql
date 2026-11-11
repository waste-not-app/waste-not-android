\set ON_ERROR_STOP on

BEGIN;
WITH owner AS (
    SELECT id
    FROM users
    WHERE name = 'Android QA 112'
      AND normalized_email = 'history-' || (:'run_id')::uuid::text || '@example.test'
)
INSERT INTO waste_logs (
    id, user_id, category, method, quantity_kind, weight_g,
    activity_date, activity_timezone, notes, client_request_id, request_hash
)
SELECT md5(:'run_id' || '-row-' || n)::uuid, owner.id,
       'PLASTIC', 'RECYCLE', 'WEIGHT', 250,
       (now() AT TIME ZONE 'Asia/Jakarta')::date, 'Asia/Jakarta', 'QA history ' || n,
       md5(:'run_id' || '-request-' || n)::uuid, md5(:'run_id' || '-fixture-' || n)
FROM owner CROSS JOIN generate_series(1, 1000) AS records(n);
COMMIT;
