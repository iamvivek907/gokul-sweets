package com.gokulsweets.restaurant.printing.service;

import com.gokulsweets.restaurant.kot.entity.Kot;
import com.gokulsweets.restaurant.printing.entity.PrintJob;
import com.gokulsweets.restaurant.printing.entity.PrinterDevice;
import com.gokulsweets.restaurant.printing.enums.PrintJobPurpose;
import com.gokulsweets.restaurant.printing.enums.PrintJobStatus;
import com.gokulsweets.restaurant.printing.enums.PrintJobType;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import com.gokulsweets.restaurant.printing.repository.PrintJobRepository;
import com.gokulsweets.restaurant.printing.repository.PrinterDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrintJobService {

    private static final int DEFAULT_MAX_ATTEMPTS =
            5;


    private final PrintJobRepository
            printJobRepository;

    private final PrinterDeviceRepository
            printerDeviceRepository;


    /*
     * =========================================================
     * ENSURE INITIAL KOT PRINT JOB
     * =========================================================
     *
     * Every KOT must have exactly one automatic INITIAL_KOT
     * print job.
     *
     * The database partial unique index on:
     *
     *      kot_id
     *      WHERE purpose = 'INITIAL_KOT'
     *
     * remains the final duplicate-protection layer.
     */

    @Transactional
    public PrintJob ensureInitialKotPrintJob(
            Kot kot
    ) {

        validateKot(
                kot
        );


        Optional<PrintJob> existingJob =
                printJobRepository
                        .findByKotIdAndPurpose(
                                kot.getId(),
                                PrintJobPurpose.INITIAL_KOT
                        );


        if (
                existingJob.isPresent()
        ) {

            PrintJob printJob =
                    existingJob.get();


            log.debug(
                    "Initial KOT print job already exists: printJobId={}, kotId={}, kotNumber={}, status={}",
                    printJob.getId(),
                    kot.getId(),
                    kot.getKotNumber(),
                    printJob.getStatus()
            );


            return printJob;
        }


        PrintJob savedJob =
                createKotPrintJob(
                        kot,
                        PrintJobPurpose.INITIAL_KOT
                );


        if (
                savedJob.getPrinter()
                        != null
        ) {

            log.info(
                    "Initial KOT print job queued: printJobId={}, kotId={}, kotNumber={}, branchId={}, printerId={}, station={}",
                    savedJob.getId(),
                    kot.getId(),
                    kot.getKotNumber(),
                    kot.getBranch()
                            .getId(),
                    savedJob.getPrinter()
                            .getId(),
                    savedJob.getStation()
            );

        } else {

            /*
             * This is not treated as a fatal error.
             *
             * The KOT and print job must remain durable even if
             * the branch has not configured its physical printer.
             */
            log.warn(
                    "Initial KOT print job queued without an assigned printer: printJobId={}, kotId={}, kotNumber={}, branchId={}, station={}",
                    savedJob.getId(),
                    kot.getId(),
                    kot.getKotNumber(),
                    kot.getBranch()
                            .getId(),
                    savedJob.getStation()
            );
        }


        return savedJob;
    }


    /*
     * =========================================================
     * CREATE KOT REPRINT JOB
     * =========================================================
     *
     * A reprint is intentionally NOT idempotent.
     *
     * Every explicit staff reprint request creates a brand-new
     * durable print_jobs row with purpose REPRINT.
     *
     * Example:
     *
     * INITIAL_KOT -> job 1
     * REPRINT     -> job 2
     * REPRINT     -> job 3
     *
     * This gives us a real print-job audit trail and avoids
     * mutating an already-completed INITIAL_KOT print job.
     */

    @Transactional
    public PrintJob createKotReprintJob(
            Kot kot
    ) {

        validateKot(
                kot
        );


        PrintJob savedJob =
                createKotPrintJob(
                        kot,
                        PrintJobPurpose.REPRINT
                );


        if (
                savedJob.getPrinter()
                        != null
        ) {

            log.info(
                    "KOT reprint job queued: printJobId={}, kotId={}, kotNumber={}, branchId={}, printerId={}, station={}",
                    savedJob.getId(),
                    kot.getId(),
                    kot.getKotNumber(),
                    kot.getBranch()
                            .getId(),
                    savedJob.getPrinter()
                            .getId(),
                    savedJob.getStation()
            );

        } else {

            log.warn(
                    "KOT reprint job queued without an assigned printer: printJobId={}, kotId={}, kotNumber={}, branchId={}, station={}",
                    savedJob.getId(),
                    kot.getId(),
                    kot.getKotNumber(),
                    kot.getBranch()
                            .getId(),
                    savedJob.getStation()
            );
        }


        return savedJob;
    }


    /*
     * =========================================================
     * CREATE PRINT JOB
     * =========================================================
     */

    private PrintJob createKotPrintJob(
            Kot kot,
            PrintJobPurpose purpose
    ) {

        Long branchId =
                kot.getBranch()
                        .getId();


        Optional<PrinterDevice> printer =
                printerDeviceRepository
                        .findFirstByBranchIdAndStationAndActiveTrueOrderByIdAsc(
                                branchId,
                                PrinterStation.KITCHEN
                        );


        LocalDateTime now =
                LocalDateTime.now();


        PrintJob printJob =
                new PrintJob();


        printJob.setBranch(
                kot.getBranch()
        );


        printJob.setKot(
                kot
        );


        printJob.setPrinter(
                printer.orElse(
                        null
                )
        );


        printJob.setJobType(
                PrintJobType.KOT
        );


        printJob.setPurpose(
                purpose
        );


        printJob.setStation(
                PrinterStation.KITCHEN
        );


        printJob.setStatus(
                PrintJobStatus.QUEUED
        );


        printJob.setCopies(
                1
        );


        printJob.setAttemptCount(
                0
        );


        printJob.setMaxAttempts(
                DEFAULT_MAX_ATTEMPTS
        );


        printJob.setQueuedAt(
                now
        );


        /*
         * New jobs are immediately claimable by the local
         * branch print agent.
         */
        printJob.setNextAttemptAt(
                now
        );


        return printJobRepository
                .saveAndFlush(
                        printJob
                );
    }


    /*
     * =========================================================
     * VALIDATION
     * =========================================================
     */

    private void validateKot(
            Kot kot
    ) {

        if (
                kot
                        ==
                        null
        ) {

            throw new IllegalArgumentException(
                    "KOT is required."
            );
        }


        if (
                kot.getId()
                        ==
                        null
        ) {

            throw new IllegalStateException(
                    "The KOT must be persisted before a print job can be created."
            );
        }


        if (
                kot.getBranch()
                        ==
                        null
                        ||
                        kot.getBranch()
                                .getId()
                                ==
                                null
        ) {

            throw new IllegalStateException(
                    "The KOT branch is unavailable."
            );
        }


        if (
                kot.getKotNumber()
                        ==
                        null
                        ||
                        kot.getKotNumber()
                                .isBlank()
        ) {

            throw new IllegalStateException(
                    "The KOT number is unavailable."
            );
        }
    }
}