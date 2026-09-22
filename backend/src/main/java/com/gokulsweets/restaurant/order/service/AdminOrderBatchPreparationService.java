package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.order.dto.admin.AdminBatchPreparationItemResponse;
import com.gokulsweets.restaurant.order.dto.admin.AdminBatchPreparationResponse;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderQueueItemResponse;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderQueueResponse;
import com.gokulsweets.restaurant.order.dto.admin.AdminStartNextPreparationRequest;
import com.gokulsweets.restaurant.order.dto.admin.AdminStartSelectedPreparationRequest;
import com.gokulsweets.restaurant.order.enums.PreparationBatchResult;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOrderBatchPreparationService {

    private static final int MAX_BATCH_SIZE =
            50;


    private final AdminOrderQueryService
            adminOrderQueryService;


    private final AdminOrderWorkflowService
            adminOrderWorkflowService;


    private final StaffAuthorizationService
            staffAuthorizationService;


    /*
     * =========================================================
     * START NEXT N
     * =========================================================
     *
     * IMPORTANT:
     *
     * This method intentionally does NOT use @Transactional.
     *
     * Every order is transitioned by
     * AdminOrderWorkflowService.startPreparationForBatch(),
     * which owns its own transaction.
     *
     * Therefore one failed order does not roll back KOTs and
     * transitions that already succeeded for other orders.
     */

    public AdminBatchPreparationResponse startNext(
            AdminStartNextPreparationRequest request
    ) {

        Long branchId =
                request.branchId();


        int requestedCount =
                validateBatchCount(
                        request.count()
                );


        requireBatchAccess(
                branchId
        );


        /*
         * The queue is already ordered by:
         *
         * pickup date ASC
         * pickup start time ASC
         * createdAt ASC
         * id ASC
         *
         * Therefore taking the first N gives us the most
         * urgent eligible orders.
         */
        AdminOrderQueueResponse queue =
                adminOrderQueryService
                        .getPreparationQueue(
                                branchId,
                                requestedCount
                        );


        List<String> orderNumbers =
                queue.orders()
                        .stream()
                        .map(
                                AdminOrderQueueItemResponse::orderNumber
                        )
                        .toList();


        log.info(
                "Start-next preparation requested: branchId={}, requestedCount={}, eligibleCandidates={}",
                branchId,
                requestedCount,
                orderNumbers.size()
        );


        return processBatch(
                branchId,
                requestedCount,
                orderNumbers
        );
    }


    /*
     * =========================================================
     * START SELECTED
     * =========================================================
     */

    public AdminBatchPreparationResponse startSelected(
            AdminStartSelectedPreparationRequest request
    ) {

        Long branchId =
                request.branchId();


        requireBatchAccess(
                branchId
        );


        List<String> originalOrderNumbers =
                request.orderNumbers();


        if (
                originalOrderNumbers.size()
                        >
                        MAX_BATCH_SIZE
        ) {

            throw new IllegalArgumentException(
                    "A maximum of 50 orders can be started at once."
            );
        }


        /*
         * Remove duplicate order numbers while preserving
         * the staff-selected order.
         */
        Set<String> uniqueNumbers =
                new LinkedHashSet<>();


        for (
                String orderNumber :
                originalOrderNumbers
        ) {

            if (
                    orderNumber != null
                            &&
                            !orderNumber.isBlank()
            ) {

                uniqueNumbers.add(
                        orderNumber.trim()
                );
            }
        }


        List<String> orderNumbers =
                new ArrayList<>(
                        uniqueNumbers
                );


        log.info(
                "Start-selected preparation requested: branchId={}, requestedCount={}, uniqueCount={}",
                branchId,
                originalOrderNumbers.size(),
                orderNumbers.size()
        );


        return processBatch(
                branchId,
                originalOrderNumbers.size(),
                orderNumbers
        );
    }


    /*
     * =========================================================
     * PROCESS BATCH
     * =========================================================
     */

    private AdminBatchPreparationResponse processBatch(
            Long branchId,
            int requested,
            List<String> orderNumbers
    ) {

        List<AdminBatchPreparationItemResponse> results =
                new ArrayList<>();


        for (
                String orderNumber :
                orderNumbers
        ) {

            try {

                PreparationBatchResult result =
                        adminOrderWorkflowService
                                .startPreparationForBatch(
                                        orderNumber,
                                        branchId
                                );


                results.add(
                        new AdminBatchPreparationItemResponse(
                                orderNumber,
                                result,
                                messageFor(
                                        result
                                )
                        )
                );

            } catch (AccessDeniedException exception) {

                /*
                 * A security failure must fail the request,
                 * not be hidden as an ordinary skipped order.
                 */
                throw exception;

            } catch (RuntimeException exception) {

                /*
                 * KOT generation/database failure for one order
                 * rolls back that order's transaction, but must
                 * not undo successful orders already processed.
                 */
                log.error(
                        "Batch preparation failed for order: branchId={}, orderNumber={}",
                        branchId,
                        orderNumber,
                        exception
                );


                results.add(
                        new AdminBatchPreparationItemResponse(
                                orderNumber,
                                PreparationBatchResult.FAILED,
                                "The order could not be started. Refresh and try again."
                        )
                );
            }
        }


        int started =
                (int) results
                        .stream()
                        .filter(
                                result ->
                                        result.result()
                                                ==
                                                PreparationBatchResult.STARTED
                        )
                        .count();


        int attempted =
                results.size();


        int skipped =
                attempted
                        -
                        started;


        LocalDateTime generatedAt =
                LocalDateTime.now();


        log.info(
                "Batch preparation completed: branchId={}, requested={}, attempted={}, started={}, skipped={}",
                branchId,
                requested,
                attempted,
                started,
                skipped
        );


        return new AdminBatchPreparationResponse(
                requested,
                attempted,
                started,
                skipped,
                List.copyOf(
                        results
                ),
                generatedAt
        );
    }


    /*
     * =========================================================
     * SECURITY
     * =========================================================
     */

    private void requireBatchAccess(
            Long branchId
    ) {

        if (branchId == null) {

            throw new IllegalArgumentException(
                    "Branch ID is required."
            );
        }


        staffAuthorizationService
                .requireBranchAccess(
                        branchId
                );


        staffAuthorizationService
                .requirePermission(
                        PermissionName.ORDER_START_PREPARATION
                );
    }


    /*
     * =========================================================
     * BATCH SIZE
     * =========================================================
     */

    private int validateBatchCount(
            Integer count
    ) {

        if (count == null) {

            throw new IllegalArgumentException(
                    "Count is required."
            );
        }


        if (
                count < 1
                        ||
                        count > MAX_BATCH_SIZE
        ) {

            throw new IllegalArgumentException(
                    "Count must be between 1 and 50."
            );
        }


        return count;
    }


    /*
     * =========================================================
     * RESULT MESSAGE
     * =========================================================
     */

    private String messageFor(
            PreparationBatchResult result
    ) {

        return switch (result) {

            case STARTED ->
                    "Preparation started and KOT created.";

            case ALREADY_PREPARING ->
                    "Order is already being prepared.";

            case NOT_ELIGIBLE ->
                    "Preparation window has not opened yet.";

            case STATUS_CHANGED ->
                    "Order status changed before it could be started.";

            case NOT_FOUND ->
                    "Order does not exist.";

            case BRANCH_MISMATCH ->
                    "Order belongs to a different branch.";

            case FAILED ->
                    "Order could not be started.";
        };
    }
}