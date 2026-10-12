-- A public label only: opaque order_number remains the identity for every API/payment.
CREATE SEQUENCE customer_order_number_seq AS bigint START WITH 1 NO CYCLE;
ALTER TABLE orders ADD COLUMN customer_order_number bigint;
ALTER TABLE orders ADD CONSTRAINT orders_customer_order_number_unique UNIQUE (customer_order_number);
ALTER TABLE orders ADD CONSTRAINT orders_customer_order_number_positive CHECK (customer_order_number > 0);

-- Existing fulfilled/confirmed orders keep working at the counter after rollout.
WITH numbered AS (
    SELECT id, nextval('customer_order_number_seq') AS number
    FROM orders
    WHERE order_status IN ('CONFIRMED','PREPARING','READY_FOR_PICKUP','PICKED_UP',
                          'READY_FOR_DELIVERY','OUT_FOR_DELIVERY','DELIVERED','PICKUP_WINDOW_EXPIRED','NO_SHOW')
    ORDER BY id
)
UPDATE orders o SET customer_order_number = n.number FROM numbered n WHERE o.id = n.id;

CREATE FUNCTION assign_customer_order_number() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.customer_order_number IS NOT NULL THEN
        -- Stable even after cancellation/refund, and never caller-reassignable.
        NEW.customer_order_number := OLD.customer_order_number;
    ELSE
        NEW.customer_order_number := NULL;
        IF NEW.order_status = 'CONFIRMED' THEN
            NEW.customer_order_number := nextval('customer_order_number_seq');
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER orders_assign_customer_order_number
BEFORE INSERT OR UPDATE ON orders
FOR EACH ROW EXECUTE FUNCTION assign_customer_order_number();
