package com.gokulsweets.restaurant.payment.repository;

import com.gokulsweets.restaurant.payment.entity.PaymentWebhookEvent;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentWebhookEventRepository
        extends JpaRepository<PaymentWebhookEvent, Long> {

    boolean existsByProviderAndProviderEventId(
            PaymentProviderType provider,
            String providerEventId
    );

    @Modifying(flushAutomatically = true)
    @Query(
            value = """
                    INSERT INTO payment_webhook_events (
                        provider,
                        provider_event_id,
                        event_type,
                        payload_sha256,
                        processed_at,
                        created_at
                    ) VALUES (
                        CAST(:provider AS VARCHAR),
                        :providerEventId,
                        :eventType,
                        :payloadSha256,
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (provider, provider_event_id)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int claim(
            @Param("provider") String provider,
            @Param("providerEventId") String providerEventId,
            @Param("eventType") String eventType,
            @Param("payloadSha256") String payloadSha256
    );
}
