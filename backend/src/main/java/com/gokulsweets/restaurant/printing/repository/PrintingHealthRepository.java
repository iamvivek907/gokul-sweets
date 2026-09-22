package com.gokulsweets.restaurant.printing.repository;

import com.gokulsweets.restaurant.printing.entity.PrintJob;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PrintingHealthRepository
        extends JpaRepository<PrintJob, Long> {

    long countByBranchIdAndStationAndStatus(
            Long branchId,
            PrinterStation station,
            PrintJobStatus status
    );


    @Query("""
            SELECT COUNT(pj)
            FROM PrintJob pj
            WHERE pj.branch.id = :branchId
              AND pj.station = :station
              AND pj.status = 'FAILED'
              AND pj.attemptCount >= pj.maxAttempts
            """)
    long countPermanentFailures(
            @Param("branchId")
            Long branchId,

            @Param("station")
            PrinterStation station
    );


    Optional<PrintJob>
    findFirstByBranchIdAndStationAndStatusOrderByPrintedAtDesc(
            Long branchId,
            PrinterStation station,
            PrintJobStatus status
    );


    Optional<PrintJob>
    findFirstByBranchIdAndStationAndStatusOrderByFailedAtDesc(
            Long branchId,
            PrinterStation station,
            PrintJobStatus status
    );
}