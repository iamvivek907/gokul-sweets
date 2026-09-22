package com.gokulsweets.restaurant.printing.repository;

import com.gokulsweets.restaurant.printing.entity.PrinterDevice;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrinterDeviceRepository
        extends JpaRepository<PrinterDevice, Long> {

    Optional<PrinterDevice>
    findFirstByBranchIdAndStationAndActiveTrueOrderByIdAsc(
            Long branchId,
            PrinterStation station
    );


    Optional<PrinterDevice>
    findByBranchIdAndCodeIgnoreCase(
            Long branchId,
            String code
    );
}