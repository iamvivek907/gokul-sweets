package com.gokulsweets.restaurant.printing.repository;

import com.gokulsweets.restaurant.printing.entity.PrinterDevice;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence operations for printer device records. */
public interface PrinterDeviceRepository extends JpaRepository<PrinterDevice, Long> {

    /**
     * Performs the find first by branch id and station and active true order by id asc operation
     * for printer device repository.
     *
     * @param branchId the branch id
     * @param station the station
     * @return the find first by branch id and station and active true order by id asc result
     */
    Optional<PrinterDevice> findFirstByBranchIdAndStationAndActiveTrueOrderByIdAsc(
            Long branchId, PrinterStation station);

    /**
     * Finds by branch id and code ignore case.
     *
     * @param branchId the branch id
     * @param code the code
     * @return the find by branch id and code ignore case result
     */
    Optional<PrinterDevice> findByBranchIdAndCodeIgnoreCase(Long branchId, String code);
}
