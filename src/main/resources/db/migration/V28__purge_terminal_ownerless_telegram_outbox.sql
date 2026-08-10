-- Ownerless Telegram rows are transient delivery state, not durable audit records.
DELETE FROM telegram_delivery_outbox
 WHERE user_id IS NULL
   AND (
       status IN ('SENT', 'FAILED')
       OR (status = 'PENDING' AND attempts >= max_attempts)
   );
