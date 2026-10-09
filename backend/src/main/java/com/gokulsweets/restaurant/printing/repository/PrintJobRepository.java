package com.gokulsweets.restaurant.printing.repository;

import com.gokulsweets.restaurant.printing.entity.PrintJob;
import com.gokulsweets.restaurant.printing.enums.PrintJobPurpose;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence operations for print job records. */
public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {

    /**
     * Finds by kot id and purpose.
     *
     * @param kotId the kot id
     * @param purpose the purpose
     * @return the find by kot id and purpose result
     */
    Optional<PrintJob> findByKotIdAndPurpose(Long kotId, PrintJobPurpose purpose);

    /**
     * Counts by branch id and status.
     *
     * @param branchId the branch id
     * @param status the status
     * @return the count by branch id and status result
     */
    long countByBranchIdAndStatus(Long branchId, PrintJobStatus status);
}
