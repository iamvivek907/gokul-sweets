package com.gokulsweets.restaurant.printing.repository;

import com.gokulsweets.restaurant.printing.entity.PrintJob;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Persistence operations for printing health records. */
public interface PrintingHealthRepository extends JpaRepository<PrintJob, Long> {

    /**
     * Performs the count by branch id and station and status operation for printing health
     * repository.
     *
     * @param branchId the branch id
     * @param station the station
     * @param status the status
     * @return the count by branch id and station and status result
     */
    long countByBranchIdAndStationAndStatus(
            Long branchId, PrinterStation station, PrintJobStatus status);

    /**
     * Counts permanent failures.
     *
     * @param branchId the branch id
     * @param station the station
     * @return the count permanent failures result
     */
    @Query(
            """
            SELECT COUNT(pj)
            FROM PrintJob pj
            WHERE pj.branch.id = :branchId
              AND pj.station = :station
              AND pj.status = 'FAILED'
              AND pj.attemptCount >= pj.maxAttempts
            """)
    long countPermanentFailures(
            @Param("branchId") Long branchId, @Param("station") PrinterStation station);

    /**
     * Performs the find first by branch id and station and status order by printed at desc
     * operation for printing health repository.
     *
     * @param branchId the branch id
     * @param station the station
     * @param status the status
     * @return the find first by branch id and station and status order by printed at desc result
     */
    Optional<PrintJob> findFirstByBranchIdAndStationAndStatusOrderByPrintedAtDesc(
            Long branchId, PrinterStation station, PrintJobStatus status);

    /**
     * Performs the find first by branch id and station and status order by failed at desc operation
     * for printing health repository.
     *
     * @param branchId the branch id
     * @param station the station
     * @param status the status
     * @return the find first by branch id and station and status order by failed at desc result
     */
    Optional<PrintJob> findFirstByBranchIdAndStationAndStatusOrderByFailedAtDesc(
            Long branchId, PrinterStation station, PrintJobStatus status);
}
