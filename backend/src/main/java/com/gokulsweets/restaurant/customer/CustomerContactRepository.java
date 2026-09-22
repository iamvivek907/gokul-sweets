package com.gokulsweets.restaurant.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CustomerContactRepository
        extends JpaRepository<CustomerContact, Long> {

    Optional<CustomerContact>
    findByNormalizedPhone(
            String normalizedPhone
    );


    /*
     * PostgreSQL UPSERT prevents duplicate contact rows when
     * two checkouts using the same phone arrive concurrently.
     */
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query(
            value = """
                    INSERT INTO customer_contacts (
                        normalized_phone,
                        latest_name,
                        verification_status,
                        first_seen_at,
                        last_seen_at,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        :normalizedPhone,
                        :latestName,
                        'UNVERIFIED',
                        :seenAt,
                        :seenAt,
                        :seenAt,
                        :seenAt
                    )
                    ON CONFLICT (normalized_phone)
                    DO UPDATE SET
                        latest_name =
                            COALESCE(
                                EXCLUDED.latest_name,
                                customer_contacts.latest_name
                            ),
                        last_seen_at =
                            GREATEST(
                                customer_contacts.last_seen_at,
                                EXCLUDED.last_seen_at
                            ),
                        updated_at =
                            EXCLUDED.updated_at
                    """,
            nativeQuery = true
    )
    void upsertGuestContact(

            @Param("normalizedPhone")
            String normalizedPhone,

            @Param("latestName")
            String latestName,

            @Param("seenAt")
            LocalDateTime seenAt
    );


    @Query(
            value = """
                    SELECT
                        cc.id AS id,
                        cc.latest_name AS latestName,
                        cc.normalized_phone AS normalizedPhone,
                        cc.verification_status AS verificationStatus,
                        cc.first_seen_at AS firstSeenAt,
                        cc.last_seen_at AS lastSeenAt,

                        COUNT(o.id) AS orderCount,

                        COUNT(o.id)
                            FILTER (
                                WHERE o.order_status = 'PICKED_UP'
                            ) AS completedPurchaseCount,

                        COALESCE(
                            SUM(o.total_amount)
                                FILTER (
                                    WHERE o.order_status = 'PICKED_UP'
                                ),
                            0
                        ) AS lifetimeSpend,

                        MAX(o.updated_at)
                            FILTER (
                                WHERE o.order_status = 'PICKED_UP'
                            ) AS lastPurchaseAt

                    FROM customer_contacts cc

                    LEFT JOIN orders o
                        ON o.customer_contact_id = cc.id

                    WHERE
                        (
                            :search IS NULL
                            OR LOWER(
                                COALESCE(
                                    cc.latest_name,
                                    ''
                                )
                            ) LIKE :searchLike
                            OR cc.normalized_phone LIKE :searchLike
                            OR CAST(cc.id AS TEXT) = :search
                        )
                        AND
                        (
                            :status IS NULL
                            OR cc.verification_status = :status
                        )

                    GROUP BY
                        cc.id,
                        cc.latest_name,
                        cc.normalized_phone,
                        cc.verification_status,
                        cc.first_seen_at,
                        cc.last_seen_at

                    ORDER BY
                        cc.last_seen_at DESC,
                        cc.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM customer_contacts cc
                    WHERE
                        (
                            :search IS NULL
                            OR LOWER(
                                COALESCE(
                                    cc.latest_name,
                                    ''
                                )
                            ) LIKE :searchLike
                            OR cc.normalized_phone LIKE :searchLike
                            OR CAST(cc.id AS TEXT) = :search
                        )
                        AND
                        (
                            :status IS NULL
                            OR cc.verification_status = :status
                        )
                    """,
            nativeQuery = true
    )
    Page<CustomerDirectoryProjection>
    searchDirectory(

            @Param("search")
            String search,

            @Param("searchLike")
            String searchLike,

            @Param("status")
            String status,

            Pageable pageable
    );

    @Query(
            value = """
                    SELECT
                        cc.id AS id,
                        cc.latest_name AS latestName,
                        cc.normalized_phone AS normalizedPhone,
                        cc.verification_status AS verificationStatus,
                        cc.first_seen_at AS firstSeenAt,
                        cc.last_seen_at AS lastSeenAt,

                        COUNT(o.id) AS orderCount,

                        COUNT(o.id)
                            FILTER (
                                WHERE o.order_status = 'PICKED_UP'
                            ) AS completedPurchaseCount,

                        COALESCE(
                            SUM(o.total_amount)
                                FILTER (
                                    WHERE o.order_status = 'PICKED_UP'
                                ),
                            0
                        ) AS lifetimeSpend,

                        MAX(o.updated_at)
                            FILTER (
                                WHERE o.order_status = 'PICKED_UP'
                            ) AS lastPurchaseAt

                    FROM customer_contacts cc

                    LEFT JOIN orders o
                        ON o.customer_contact_id = cc.id

                    WHERE cc.id = :customerContactId

                    GROUP BY
                        cc.id,
                        cc.latest_name,
                        cc.normalized_phone,
                        cc.verification_status,
                        cc.first_seen_at,
                        cc.last_seen_at
                    """,
            nativeQuery = true
    )
    Optional<CustomerDirectoryProjection>
    findDirectoryItemById(

            @Param("customerContactId")
            Long customerContactId
    );

}
