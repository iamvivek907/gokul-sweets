CREATE UNIQUE INDEX uk_payments_one_pending_per_order
ON payments(order_id)
WHERE payment_status = 'PENDING';