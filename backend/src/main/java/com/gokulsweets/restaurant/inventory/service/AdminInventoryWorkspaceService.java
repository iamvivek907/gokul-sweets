package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.branchproduct.BranchProductRepository;
import com.gokulsweets.restaurant.inventory.dto.*;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.inventory.model.InventoryAvailability;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminInventoryWorkspaceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BranchProductRepository branchProductRepository;
    private final BranchInventoryPolicyRepository policyRepository;
    private final InventoryDailyAllocationRepository allocationRepository;
    private final InventoryAvailabilityService availabilityService;
    private final AdminInventoryService adminInventoryService;

    @Transactional(readOnly = true)
    public InventoryCataloguePageResponse getCatalogue(
            Long branchId,
            LocalDate serviceDate,
            String search,
            Long categoryId,
            InventoryCatalogueFilter filter,
            int page,
            int size
    ) {
        validatePage(page, size);

        List<BranchProduct> products =
                branchProductRepository.findAdminMenu(branchId);

        List<Long> branchProductIds = products.stream()
                .map(BranchProduct::getId)
                .toList();

        Map<Long, BranchInventoryPolicy> policies = policyRepository
                .findByBranchProductIdIn(branchProductIds)
                .stream()
                .collect(Collectors.toMap(
                        policy -> policy.getBranchProduct().getId(),
                        Function.identity()
                ));

        Map<Long, InventoryDailyAllocation> allocations = allocationRepository
                .findByBranchProductIdInAndServiceDate(
                        branchProductIds,
                        serviceDate
                )
                .stream()
                .collect(Collectors.toMap(
                        allocation -> allocation.getBranchProduct().getId(),
                        Function.identity()
                ));

        List<InventoryCatalogueItemResponse> allItems = products.stream()
                .map(product -> toCatalogueItem(
                        product,
                        policies.get(product.getId()),
                        allocations.get(product.getId())
                ))
                .toList();

        InventoryCatalogueSummaryResponse summary = summary(allItems);
        List<InventoryCategoryOptionResponse> categories = products.stream()
                .map(product -> new InventoryCategoryOptionResponse(
                        product.getProduct().getCategory().getId(),
                        product.getProduct().getCategory().getName()
                ))
                .distinct()
                .toList();
        String normalizedSearch = normalize(search);
        InventoryCatalogueFilter appliedFilter =
                filter == null ? InventoryCatalogueFilter.ALL : filter;

        List<InventoryCatalogueItemResponse> filtered = allItems.stream()
                .filter(item -> categoryId == null
                        || item.categoryId().equals(categoryId))
                .filter(item -> matchesSearch(item, normalizedSearch))
                .filter(item -> matchesFilter(item, appliedFilter))
                .toList();

        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        int totalPages = filtered.isEmpty()
                ? 0
                : (int) Math.ceil((double) filtered.size() / size);

        return new InventoryCataloguePageResponse(
                branchId,
                serviceDate,
                List.copyOf(filtered.subList(fromIndex, toIndex)),
                page,
                size,
                filtered.size(),
                totalPages,
                summary,
                categories
        );
    }

    @Transactional
    public AdminBulkInventoryResponse<InventoryPolicyResponse> updatePolicies(
            Long branchId,
            AdminBulkPolicyRequest request
    ) {
        validateBranchProducts(branchId, request.branchProductIds());

        List<InventoryPolicyResponse> results = request.branchProductIds()
                .stream()
                .distinct()
                .map(id -> adminInventoryService.upsertPolicy(
                        id,
                        request.policy()
                ))
                .toList();

        return new AdminBulkInventoryResponse<>(results.size(), results);
    }

    @Transactional
    public AdminBulkInventoryResponse<InventoryAllocationResponse> approveAllocations(
            Long branchId,
            AdminBulkAllocationRequest request,
            String actor
    ) {
        List<Long> ids = request.items().stream()
                .map(AdminBulkAllocationItemRequest::branchProductId)
                .toList();
        validateNoDuplicates(ids);
        validateBranchProducts(branchId, ids);

        List<InventoryAllocationResponse> results = request.items().stream()
                .map(item -> adminInventoryService.approveAllocation(
                        item.branchProductId(),
                        request.serviceDate(),
                        new AdminAllocationApprovalRequest(
                                item.approvedQuantity(),
                                item.safetyBufferQuantity(),
                                item.forecastQuantity(),
                                item.forecastConfidence(),
                                item.expectedReadyAt(),
                                item.note()
                        ),
                        actor
                ))
                .toList();

        return new AdminBulkInventoryResponse<>(results.size(), results);
    }

    @Transactional
    public AdminBulkInventoryResponse<InventoryAllocationResponse> updateReadiness(
            Long branchId,
            AdminBulkReadinessRequest request,
            String actor
    ) {
        List<Long> ids = request.items().stream()
                .map(AdminBulkReadinessItemRequest::branchProductId)
                .toList();
        validateNoDuplicates(ids);
        validateBranchProducts(branchId, ids);

        List<InventoryAllocationResponse> results = request.items().stream()
                .map(item -> adminInventoryService.updateReadiness(
                        item.branchProductId(),
                        request.serviceDate(),
                        new AdminReadinessUpdateRequest(
                                item.status(),
                                item.readyQuantity(),
                                item.expectedReadyAt(),
                                item.note()
                        ),
                        actor
                ))
                .toList();

        return new AdminBulkInventoryResponse<>(results.size(), results);
    }

    @Transactional
    public AdminBulkInventoryResponse<InventoryAllocationResponse> completeSetup(
            Long branchId,
            AdminCompleteInventoryRequest request,
            String actor
    ) {
        List<Long> ids = request.items().stream()
                .map(AdminCompleteInventoryItemRequest::branchProductId)
                .toList();
        validateNoDuplicates(ids);
        validateBranchProducts(branchId, ids);

        List<InventoryAllocationResponse> results = request.items().stream()
                .map(item -> {
                    InventoryAllocationResponse approved =
                            adminInventoryService.approveAllocation(
                            item.branchProductId(),
                            request.serviceDate(),
                            new AdminAllocationApprovalRequest(
                                    item.approvedQuantity(),
                                    item.safetyBufferQuantity(),
                                    item.forecastQuantity(),
                                    item.forecastConfidence(),
                                    item.expectedReadyAt(),
                                    item.note()
                            ),
                            actor
                    );

                    boolean markReady = item.markReady() == null
                            || item.markReady();

                    if (!markReady) {
                        return approved;
                    }

                    if (item.readyQuantity().signum() <= 0) {
                        throw new InventoryConflictException(
                                "READY_QUANTITY_REQUIRED",
                                "Ready quantity must be greater than zero when an item is marked ready."
                        );
                    }

                    return adminInventoryService.updateReadiness(
                            item.branchProductId(),
                            request.serviceDate(),
                            new AdminReadinessUpdateRequest(
                                    InventoryAllocationStatus.READY,
                                    item.readyQuantity(),
                                    item.expectedReadyAt(),
                                    item.note()
                            ),
                            actor
                    );
                })
                .toList();

        return new AdminBulkInventoryResponse<>(results.size(), results);
    }

    @Transactional(readOnly = true)
    public Long getBranchIdForBranchProduct(Long branchProductId) {
        return branchProductRepository.findById(branchProductId)
                .map(branchProduct -> branchProduct.getBranch().getId())
                .orElseThrow(() -> new InventoryNotFoundException(
                        "BRANCH_PRODUCT_NOT_FOUND",
                        "The selected branch product does not exist."
                ));
    }

    private InventoryCatalogueItemResponse toCatalogueItem(
            BranchProduct branchProduct,
            BranchInventoryPolicy policy,
            InventoryDailyAllocation allocation
    ) {
        InventoryPolicyResponse policyResponse = policy == null
                ? null
                : InventoryPolicyResponse.from(policy);

        InventoryAllocationResponse allocationResponse = null;
        if (policy != null && allocation != null) {
            InventoryAvailability availability =
                    availabilityService.calculate(allocation, policy);
            allocationResponse = InventoryAllocationResponse.from(
                    allocation,
                    availability
            );
        }

        Attention attention = attention(policy, allocation, allocationResponse);

        return new InventoryCatalogueItemResponse(
                branchProduct.getId(),
                branchProduct.getProduct().getId(),
                branchProduct.getProduct().getCategory().getId(),
                branchProduct.getProduct().getCategory().getName(),
                branchProduct.getProduct().getCode(),
                branchProduct.getProduct().getName(),
                branchProduct.getProduct().getSaleMode().name(),
                branchProduct.isAvailable(),
                policyResponse,
                allocationResponse,
                attention.required(),
                attention.code(),
                attention.message()
        );
    }

    private Attention attention(
            BranchInventoryPolicy policy,
            InventoryDailyAllocation allocation,
            InventoryAllocationResponse response
    ) {
        if (policy == null) {
            return new Attention(true, "POLICY_MISSING", "Configure inventory rules.");
        }
        if (!policy.isOnlineEnabled()) {
            return new Attention(false, null, null);
        }
        if (allocation == null) {
            return new Attention(true, "ALLOCATION_MISSING", "Approve an online allocation for this date.");
        }
        if (allocation.getStatus() == InventoryAllocationStatus.DRAFT) {
            return new Attention(true, "ALLOCATION_DRAFT", "Review and approve the draft allocation.");
        }
        if (allocation.getStatus() == InventoryAllocationStatus.DELAYED) {
            return new Attention(true, "PREPARATION_DELAYED", "Stock is delayed; confirm the new ready time.");
        }
        if (allocation.getStatus() == InventoryAllocationStatus.UNAVAILABLE) {
            return new Attention(true, "UNAVAILABLE", "This item is unavailable for the selected date.");
        }
        if (response != null && response.availableQuantity().signum() <= 0) {
            return new Attention(true, "NO_AVAILABLE_QUANTITY", "No online quantity remains after holds and safety buffer.");
        }
        return new Attention(false, null, null);
    }

    private InventoryCatalogueSummaryResponse summary(
            List<InventoryCatalogueItemResponse> items
    ) {
        return new InventoryCatalogueSummaryResponse(
                items.size(),
                items.stream().filter(item -> item.policy() != null
                        && item.policy().onlineEnabled()).count(),
                items.stream().filter(item -> item.allocation() != null
                        && "READY".equals(item.allocation().status())).count(),
                items.stream().filter(InventoryCatalogueItemResponse::needsAttention).count(),
                items.stream().filter(item -> item.policy() == null).count(),
                items.stream().filter(item -> item.allocation() != null
                        && "DELAYED".equals(item.allocation().status())).count(),
                items.stream().filter(item -> item.allocation() != null
                        && "UNAVAILABLE".equals(item.allocation().status())).count()
        );
    }

    private boolean matchesSearch(
            InventoryCatalogueItemResponse item,
            String search
    ) {
        if (search == null) {
            return true;
        }
        return item.productName().toLowerCase(Locale.ROOT).contains(search)
                || item.productCode().toLowerCase(Locale.ROOT).contains(search)
                || item.categoryName().toLowerCase(Locale.ROOT).contains(search);
    }

    private boolean matchesFilter(
            InventoryCatalogueItemResponse item,
            InventoryCatalogueFilter filter
    ) {
        return switch (filter) {
            case ALL -> true;
            case NEEDS_ATTENTION -> item.needsAttention();
            case NOT_CONFIGURED -> item.policy() == null;
            case DRAFT, APPROVED, READY, DELAYED, UNAVAILABLE ->
                    item.allocation() != null
                            && filter.name().equals(item.allocation().status());
        };
    }

    private void validateBranchProducts(
            Long branchId,
            Collection<Long> branchProductIds
    ) {
        if (branchProductIds == null || branchProductIds.isEmpty()) {
            throw new InventoryConflictException(
                    "INVENTORY_SELECTION_REQUIRED",
                    "Select at least one product."
            );
        }
        long matching = branchProductRepository.countByIdInAndBranchId(
                new HashSet<>(branchProductIds),
                branchId
        );
        if (matching != new HashSet<>(branchProductIds).size()) {
            throw new InventoryConflictException(
                    "BRANCH_PRODUCT_MISMATCH",
                    "One or more selected products do not belong to this branch."
            );
        }
    }

    private void validateNoDuplicates(List<Long> ids) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new InventoryConflictException(
                    "DUPLICATE_INVENTORY_ITEM",
                    "The same product cannot appear more than once in a bulk request."
            );
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InventoryConflictException(
                    "INVALID_PAGE_REQUEST",
                    "Page must be non-negative and size must be between 1 and 100."
            );
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record Attention(
            boolean required,
            String code,
            String message
    ) {
    }
}
