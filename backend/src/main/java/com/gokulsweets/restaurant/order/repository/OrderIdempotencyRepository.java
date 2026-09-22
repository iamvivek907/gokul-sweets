package com.gokulsweets.restaurant.order.repository;

import com.gokulsweets.restaurant.order.entity.OrderIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderIdempotencyRepository
        extends JpaRepository<OrderIdempotency, Long> {

    Optional<OrderIdempotency> findByIdempotencyKey(
            String idempotencyKey
    );

    @Modifying
    @Query(
            value = """
                    INSERT INTO order_idempotency (
                        idempotency_key,
                        request_hash,
                        created_at
                    )
                    VALUES (
                        :idempotencyKey,
                        :requestHash,
                        CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (idempotency_key)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int claim(
            @Param("idempotencyKey")
            String idempotencyKey,

            @Param("requestHash")
            String requestHash
    );

    @Modifying
    @Query(
            value = """
                    UPDATE order_idempotency
                    SET order_id = :orderId
                    WHERE idempotency_key = :idempotencyKey
                    """,
            nativeQuery = true
    )
    int linkOrder(
            @Param("idempotencyKey")
            String idempotencyKey,

            @Param("orderId")
            Long orderId
    );
}