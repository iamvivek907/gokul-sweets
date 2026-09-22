package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.branchproduct.BranchProductRepository;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.dto.*;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryTransactionType;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.inventory.model.InventoryAvailability;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminInventoryService {

    private final BranchProductRepository branchProductRepository;
    private final BranchInventoryPolicyRepository policyRepository;
    private final InventoryDailyAllocationRepository allocationRepository;
    private final InventoryAvailabilityService availabilityService;
    private final InventoryLedgerService ledgerService;
    private final InventoryQuantityService quantityService;
    private final InventoryProperties properties;
    private final Clock inventoryClock;

    @Transactional
    public InventoryPolicyResponse upsertPolicy(
            Long branchProductId,
            AdminInventoryPolicyRequest request
    ) {
        BranchProduct branchProduct = branchProductRepository
                .findById(branchProductId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "BRANCH_PRODUCT_NOT_FOUND",
                        "The selected branch product does not exist."
                ));

        validatePolicyRequest(
                request,
                branchProduct
        );

        BranchInventoryPolicy policy = policyRepository
                .findByBranchProductId(branchProductId)
                .orElseGet(BranchInventoryPolicy::new);

        policy.setBranchProduct(branchProduct);
        policy.setControlMode(request.controlMode());
        policy.setInventoryUnit(request.inventoryUnit());
        policy.setOnlineEnabled(request.onlineEnabled());
        policy.setReadyStockRequired(request.readyStockRequired());
        policy.setDefaultSafetyBuffer(
                quantityService.normalizeNonNegative(
                        request.defaultSafetyBuffer(),
                        request.inventoryUnit(),
                        "Default safety buffer"
                )
        );
        policy.setMaximumDailyAllocation(
                quantityService.normalizeNonNegative(
                        request.maximumDailyAllocation(),
                        request.inventoryUnit(),
                        "Maximum daily allocation"
                )
        );
        policy.setBookingHorizonDays(
                request.bookingHorizonDays() == null
                        ? properties.getDefaultBookingHorizonDays()
                        : request.bookingHorizonDays()
        );
        policy.setProductionLeadMinutes(request.productionLeadMinutes());
        policy.setShelfLifeMinutes(request.shelfLifeMinutes());

        BranchInventoryPolicy saved = policyRepository.save(policy);

        log.info(
                "Inventory policy saved: branchProductId={}, controlMode={}, unit={}, onlineEnabled={}",
                branchProductId,
                saved.getControlMode(),
                saved.getInventoryUnit(),
                saved.isOnlineEnabled()
        );

        return InventoryPolicyResponse.from(saved);
    }

    @Transactional
    public InventoryAllocationResponse approveAllocation(
            Long branchProductId,
            LocalDate serviceDate,
            AdminAllocationApprovalRequest request,
            String performedBy
    ) {
        BranchInventoryPolicy policy = getPolicy(branchProductId);
        validateServiceDate(serviceDate, policy);
        BigDecimal approvedQuantity =
                quantityService.normalizePositive(
                        request.approvedQuantity(),
                        policy.getInventoryUnit(),
                        "Approved quantity"
                );

        BigDecimal safetyBuffer =
                request.safetyBufferQuantity() == null
                        ? policy.getDefaultSafetyBuffer()
                        : quantityService.normalizeNonNegative(
                        request.safetyBufferQuantity(),
                        policy.getInventoryUnit(),
                        "Safety buffer"
                );

        validateApprovedQuantity(approvedQuantity, policy);

        InventoryDailyAllocation allocation = allocationRepository
                .findForUpdate(branchProductId, serviceDate)
                .orElseGet(() -> newAllocation(
                        policy,
                        serviceDate
                ));

        ensureCommittedQuantityStillCovered(
                allocation,
                approvedQuantity,
                safetyBuffer
        );

        BigDecimal previousApproved = allocation.getApprovedQuantity();

        allocation.setApprovedQuantity(approvedQuantity);
        allocation.setSafetyBufferQuantity(safetyBuffer);
        allocation.setForecastQuantity(
                quantityService.normalizeNonNegative(
                        request.forecastQuantity(),
                        policy.getInventoryUnit(),
                        "Forecast quantity"
                )
        );
        allocation.setForecastConfidence(normalize(request.forecastConfidence()));
        allocation.setExpectedReadyAt(request.expectedReadyAt());
        allocation.setNote(normalize(request.note()));
        allocation.setApprovedBy(performedBy);
        allocation.setApprovedAt(LocalDateTime.now(inventoryClock));
        allocation.setStatus(
                InventoryAllocationStatus.APPROVED
        );

        InventoryDailyAllocation saved = allocationRepository.save(allocation);

        BigDecimal delta = approvedQuantity.subtract(previousApproved);
        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            ledgerService.record(
                    saved,
                    null,
                    InventoryTransactionType.ALLOCATION_APPROVED,
                    delta,
                    null,
                    "allocation:" + saved.getId(),
                    "Daily online allocation approved.",
                    performedBy
            );
        }

        log.info(
                "Inventory allocation approved: branchProductId={}, serviceDate={}, approvedQuantity={}, safetyBuffer={}",
                branchProductId,
                serviceDate,
                saved.getApprovedQuantity(),
                saved.getSafetyBufferQuantity()
        );

        return toResponse(saved, policy);
    }

    @Transactional
    public InventoryAllocationResponse updateReadiness(
            Long branchProductId,
            LocalDate serviceDate,
            AdminReadinessUpdateRequest request,
            String performedBy
    ) {
        if (
                request.status() != InventoryAllocationStatus.READY
                        && request.status() != InventoryAllocationStatus.DELAYED
                        && request.status() != InventoryAllocationStatus.UNAVAILABLE
        ) {
            throw new InventoryConflictException(
                    "INVALID_READINESS_STATUS",
                    "Readiness can only be READY, DELAYED or UNAVAILABLE."
            );
        }

        BranchInventoryPolicy policy = getPolicy(branchProductId);
        InventoryDailyAllocation allocation = allocationRepository
                .findForUpdate(branchProductId, serviceDate)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "ALLOCATION_NOT_FOUND",
                        "No inventory allocation exists for this product and date."
                ));

        BigDecimal readyQuantity =
                quantityService.normalizeNonNegative(
                        request.readyQuantity(),
                        policy.getInventoryUnit(),
                        "Ready quantity"
                );

        BigDecimal previousReady = allocation.getReadyQuantity();

        if (
                request.status() == InventoryAllocationStatus.READY
                        && readyQuantity.compareTo(BigDecimal.ZERO) <= 0
        ) {
            throw new InventoryConflictException(
                    "READY_QUANTITY_REQUIRED",
                    "Ready quantity must be greater than zero when stock is marked ready."
            );
        }

        BigDecimal protectedQuantity =
                allocation.getHeldQuantity()
                        .add(allocation.getCommittedQuantity());

        if (
                policy.isReadyStockRequired()
                        && readyQuantity.compareTo(protectedQuantity) < 0
        ) {
            throw new InventoryConflictException(
                    "READY_QUANTITY_BELOW_COMMITMENTS",
                    "Ready quantity cannot be lower than existing holds and confirmed commitments."
            );
        }

        if (
                request.status() == InventoryAllocationStatus.UNAVAILABLE
                        && protectedQuantity.compareTo(BigDecimal.ZERO) > 0
        ) {
            throw new InventoryConflictException(
                    "ALLOCATION_HAS_COMMITMENTS",
                    "This allocation has customer commitments. Resolve the affected orders before marking it unavailable."
            );
        }

        allocation.setStatus(request.status());
        allocation.setReadyQuantity(readyQuantity);
        allocation.setExpectedReadyAt(request.expectedReadyAt());
        allocation.setActualReadyAt(
                request.status() == InventoryAllocationStatus.READY
                        ? LocalDateTime.now(inventoryClock)
                        : null
        );
        allocation.setNote(normalize(request.note()));

        InventoryDailyAllocation saved = allocationRepository.save(allocation);

        BigDecimal delta = readyQuantity.subtract(previousReady);
        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            ledgerService.record(
                    saved,
                    null,
                    InventoryTransactionType.READY_STOCK_RECORDED,
                    delta,
                    null,
                    "readiness:" + saved.getId(),
                    "Ready stock updated.",
                    performedBy
            );
        }

        return toResponse(saved, policy);
    }

    @Transactional(readOnly = true)
    public List<InventoryAllocationResponse> getAllocations(
            Long branchId,
            LocalDate serviceDate
    ) {
        return allocationRepository
                .findByBranchProduct_Branch_IdAndServiceDateOrderByBranchProduct_Product_NameAsc(
                        branchId,
                        serviceDate
                )
                .stream()
                .map(allocation -> {
                    BranchInventoryPolicy policy = getPolicy(
                            allocation.getBranchProduct().getId()
                    );
                    return toResponse(allocation, policy);
                })
                .toList();
    }

    private InventoryAllocationResponse toResponse(
            InventoryDailyAllocation allocation,
            BranchInventoryPolicy policy
    ) {
        InventoryAvailability availability =
                availabilityService.calculate(allocation, policy);

        return InventoryAllocationResponse.from(
                allocation,
                availability
        );
    }

    private InventoryDailyAllocation newAllocation(
            BranchInventoryPolicy policy,
            LocalDate serviceDate
    ) {
        InventoryDailyAllocation allocation =
                new InventoryDailyAllocation();
        allocation.setBranchProduct(policy.getBranchProduct());
        allocation.setServiceDate(serviceDate);
        allocation.setInventoryUnit(policy.getInventoryUnit());
        allocation.setSafetyBufferQuantity(
                policy.getDefaultSafetyBuffer()
        );
        return allocation;
    }

    private BranchInventoryPolicy getPolicy(
            Long branchProductId
    ) {
        return policyRepository
                .findByBranchProductId(branchProductId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "INVENTORY_POLICY_NOT_FOUND",
                        "Inventory policy is not configured for this branch product."
                ));
    }

    private void validateServiceDate(
            LocalDate serviceDate,
            BranchInventoryPolicy policy
    ) {
        LocalDate today = LocalDate.now(inventoryClock);
        LocalDate lastAllowed = today.plusDays(
                policy.getBookingHorizonDays()
        );

        if (serviceDate.isBefore(today)) {
            throw new InventoryConflictException(
                    "PAST_ALLOCATION_DATE",
                    "Inventory cannot be approved for a past date."
            );
        }

        if (serviceDate.isAfter(lastAllowed)) {
            throw new InventoryConflictException(
                    "ALLOCATION_OUTSIDE_BOOKING_HORIZON",
                    "The selected date is outside this product's booking horizon."
            );
        }
    }

    private void validateApprovedQuantity(
            BigDecimal quantity,
            BranchInventoryPolicy policy
    ) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InventoryConflictException(
                    "APPROVED_QUANTITY_REQUIRED",
                    "Approved quantity must be greater than zero."
            );
        }

        if (
                policy.getMaximumDailyAllocation() != null
                        && quantity.compareTo(
                        policy.getMaximumDailyAllocation()
                ) > 0
        ) {
            throw new InventoryConflictException(
                    "MAXIMUM_ALLOCATION_EXCEEDED",
                    "Approved quantity exceeds the configured daily maximum."
            );
        }
    }

    private void ensureCommittedQuantityStillCovered(
            InventoryDailyAllocation allocation,
            BigDecimal approvedQuantity,
            BigDecimal buffer
    ) {
        BigDecimal protectedQuantity = allocation.getHeldQuantity()
                .add(allocation.getCommittedQuantity())
                .add(buffer);

        if (approvedQuantity.compareTo(protectedQuantity) < 0) {
            throw new InventoryConflictException(
                    "ALLOCATION_BELOW_COMMITMENTS",
                    "Approved quantity cannot be lower than existing holds, commitments and safety buffer."
            );
        }
    }

    private void validatePolicyRequest(
            AdminInventoryPolicyRequest request,
            BranchProduct branchProduct
    ) {
        quantityService.validatePolicyUnit(
                branchProduct.getProduct().getSaleMode(),
                request.controlMode(),
                request.inventoryUnit()
        );

        if (
                request.maximumDailyAllocation() != null
                        && request.defaultSafetyBuffer().compareTo(
                        request.maximumDailyAllocation()
                ) >= 0
        ) {
            throw new InventoryConflictException(
                    "INVALID_SAFETY_BUFFER",
                    "Safety buffer must be lower than the maximum daily allocation."
            );
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
