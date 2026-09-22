package com.gokulsweets.restaurant.printing.repository;

import com.gokulsweets.restaurant.printing.entity.PrintJob;
import com.gokulsweets.restaurant.printing.enums.PrintJobPurpose;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrintJobRepository
        extends JpaRepository<PrintJob, Long> {

    Optional<PrintJob>
    findByKotIdAndPurpose(
            Long kotId,
            PrintJobPurpose purpose
    );


    long countByBranchIdAndStatus(
            Long branchId,
            PrintJobStatus status
    );
}