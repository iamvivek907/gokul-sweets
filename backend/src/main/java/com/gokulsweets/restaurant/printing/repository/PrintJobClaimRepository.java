package com.gokulsweets.restaurant.printing.repository;

import com.gokulsweets.restaurant.printing.entity.PrintJob;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PrintJobClaimRepository {

    @PersistenceContext
    private EntityManager entityManager;


    /*
     * =========================================================
     * CLAIM CANDIDATE
     * =========================================================
     *
     * PostgreSQL FOR UPDATE SKIP LOCKED means that two print
     * agents can request work simultaneously without blocking
     * each other or normally claiming the same row.
     *
     * Eligible states:
     *
     * QUEUED
     *      immediately / next_attempt_at reached
     *
     * FAILED
     *      retry time reached
     *
     * CLAIMED
     *      previous agent lease expired
     */

    public Optional<PrintJob> findNextClaimableJobForUpdate(
            Long branchId,
            PrinterStation station,
            LocalDateTime now
    ) {

        @SuppressWarnings("unchecked")
        List<Number> ids =
                entityManager
                        .createNativeQuery("""
                                SELECT pj.id
                                FROM print_jobs pj
                                WHERE pj.branch_id = :branchId
                                  AND pj.station = :station
                                  AND pj.attempt_count < pj.max_attempts
                                  AND (
                                        (
                                            pj.status = 'QUEUED'
                                            AND (
                                                pj.next_attempt_at IS NULL
                                                OR pj.next_attempt_at <= :now
                                            )
                                        )
                                        OR
                                        (
                                            pj.status = 'FAILED'
                                            AND pj.next_attempt_at IS NOT NULL
                                            AND pj.next_attempt_at <= :now
                                        )
                                        OR
                                        (
                                            pj.status = 'CLAIMED'
                                            AND pj.claim_expires_at IS NOT NULL
                                            AND pj.claim_expires_at <= :now
                                        )
                                      )
                                ORDER BY
                                    pj.queued_at ASC,
                                    pj.id ASC
                                FOR UPDATE SKIP LOCKED
                                LIMIT 1
                                """)
                        .setParameter(
                                "branchId",
                                branchId
                        )
                        .setParameter(
                                "station",
                                station.name()
                        )
                        .setParameter(
                                "now",
                                now
                        )
                        .getResultList();


        if (
                ids.isEmpty()
        ) {

            return Optional.empty();
        }


        Long id =
                ids.get(0)
                        .longValue();


        PrintJob printJob =
                entityManager.find(
                        PrintJob.class,
                        id
                );


        return Optional.ofNullable(
                printJob
        );
    }
}