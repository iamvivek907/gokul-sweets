package com.gokulsweets.restaurant.printing.repository;

import com.gokulsweets.restaurant.printing.entity.PrintJob;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Persistence operations for admin print job records. */
public interface AdminPrintJobRepository extends JpaRepository<PrintJob, Long> {

    /**
     * Finds admin jobs.
     *
     * @param branchId the branch id
     * @param status the status
     * @param pageable the pageable
     * @return the find admin jobs result
     */
    @EntityGraph(attributePaths = {"branch", "printer", "kot", "kot.order"})
    @Query(
            """
            SELECT pj
            FROM PrintJob pj
            WHERE pj.branch.id = :branchId
              AND (
                    :status IS NULL
                    OR pj.status = :status
              )
            ORDER BY pj.createdAt DESC, pj.id DESC
            """)
    Page<PrintJob> findAdminJobs(
            @Param("branchId") Long branchId,
            @Param("status") PrintJobStatus status,
            Pageable pageable);

    /**
     * Counts by branch id and status.
     *
     * @param branchId the branch id
     * @param status the status
     * @return the count by branch id and status result
     */
    long countByBranchIdAndStatus(Long branchId, PrintJobStatus status);

    /**
     * Counts permanent failures.
     *
     * @param branchId the branch id
     * @return the count permanent failures result
     */
    @Query(
            """
            SELECT COUNT(pj)
            FROM PrintJob pj
            WHERE pj.branch.id = :branchId
              AND pj.status = 'FAILED'
              AND pj.attemptCount >= pj.maxAttempts
            """)
    long countPermanentFailures(@Param("branchId") Long branchId);

    /**
     * Finds for retry.
     *
     * @param printJobId the print job id
     * @return the find for retry result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"branch", "printer", "kot", "kot.order"})
    @Query(
            """
            SELECT pj
            FROM PrintJob pj
            WHERE pj.id = :printJobId
            """)
    Optional<PrintJob> findForRetry(@Param("printJobId") Long printJobId);
}
