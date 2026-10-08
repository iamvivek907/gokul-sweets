package com.gokulsweets.restaurant.branchproduct;

import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.branchproduct.dto.AdminBranchProductResponse;
import com.gokulsweets.restaurant.branchproduct.dto.AdminBranchProductUpdateRequest;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Coordinates admin branch menu operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBranchMenuService {

    private final BranchRepository branchRepository;

    private final BranchProductRepository branchProductRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    /*
     * =========================================================
     * GET ADMIN MENU
     * =========================================================
     */
    /**
     * Returns branch menu.
     *
     * @param branchId the branch id
     * @return the get branch menu result
     */
    @Transactional(readOnly = true)
    public List<AdminBranchProductResponse> getBranchMenu(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchMenuService.class, "getBranchMenu(Long)");
        try {
            validateBranchId(branchId);
            authorize(branchId);
            requireBranchExists(branchId);
            List<BranchProduct> branchProducts = branchProductRepository.findAdminMenu(branchId);
            log.debug(
                    "Loaded {} admin menu items for branchId={}", branchProducts.size(), branchId);
            return branchProducts.stream().map(AdminBranchProductResponse::from).toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminBranchMenuService.class, "getBranchMenu(Long)");
        }
    }

    /*
     * =========================================================
     * UPDATE BRANCH MENU ITEM
     * =========================================================
     */
    /**
     * Updates branch product.
     *
     * @param branchId the branch id
     * @param productId the product id
     * @param request the request
     * @return the update branch product result
     */
    @Transactional
    public AdminBranchProductResponse updateBranchProduct(
            Long branchId, Long productId, AdminBranchProductUpdateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchMenuService.class,
                        "updateBranchProduct(Long,Long,AdminBranchProductUpdateRequest)");
        try {
            validateBranchId(branchId);
            validateProductId(productId);
            if (request == null) {
                throw new IllegalArgumentException("Update request is required.");
            }
            authorize(branchId);
            requireBranchExists(branchId);
            BranchProduct branchProduct =
                    branchProductRepository
                            .findByBranchIdAndProductId(branchId, productId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Product is not configured for this branch."));
            boolean changed = false;
            /*
             * -----------------------------------------------------
             * AVAILABILITY
             * -----------------------------------------------------
             */
            if (request.available() != null) {
                if (branchProduct.isAvailable() != request.available()) {
                    branchProduct.setAvailable(request.available());
                    changed = true;
                }
            }
            /*
             * -----------------------------------------------------
             * PRICE OVERRIDE
             * -----------------------------------------------------
             */
            if (Boolean.TRUE.equals(request.clearPriceOverride())) {
                if (branchProduct.getPriceOverride() != null) {
                    branchProduct.setPriceOverride(null);
                    changed = true;
                }
            } else if (request.priceOverride() != null) {
                if (branchProduct.getPriceOverride() == null
                        || branchProduct.getPriceOverride().compareTo(request.priceOverride())
                                != 0) {
                    branchProduct.setPriceOverride(request.priceOverride());
                    changed = true;
                }
            }
            /*
             * -----------------------------------------------------
             * DISPLAY ORDER
             * -----------------------------------------------------
             */
            if (request.displayOrder() != null) {
                if (!request.displayOrder().equals(branchProduct.getDisplayOrder())) {
                    branchProduct.setDisplayOrder(request.displayOrder());
                    changed = true;
                }
            }
            if (changed) {
                branchProductRepository.save(branchProduct);
                log.info("Updated branch menu item branchId={}, productId={}", branchId, productId);
            } else {
                log.debug(
                        "No branch menu changes detected branchId={}, productId={}",
                        branchId,
                        productId);
            }
            return AdminBranchProductResponse.from(branchProduct);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchMenuService.class,
                    "updateBranchProduct(Long,Long,AdminBranchProductUpdateRequest)");
        }
    }

    /*
     * =========================================================
     * AUTHORIZATION
     * =========================================================
     */
    /**
     * Authorizes the operation.
     *
     * @param branchId the branch id
     */
    private void authorize(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchMenuService.class, "authorize(Long)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.MENU_MANAGE);
            staffAuthorizationService.requireBranchAccess(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminBranchMenuService.class, "authorize(Long)");
        }
    }

    /*
     * =========================================================
     * VALIDATION
     * =========================================================
     */
    /**
     * Validates branch id.
     *
     * @param branchId the branch id
     */
    private void validateBranchId(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchMenuService.class, "validateBranchId(Long)");
        try {
            if (branchId == null || branchId <= 0) {
                throw new IllegalArgumentException("Valid branchId is required.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchMenuService.class,
                    "validateBranchId(Long)");
        }
    }

    /**
     * Validates product id.
     *
     * @param productId the product id
     */
    private void validateProductId(Long productId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchMenuService.class, "validateProductId(Long)");
        try {
            if (productId == null || productId <= 0) {
                throw new IllegalArgumentException("Valid productId is required.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchMenuService.class,
                    "validateProductId(Long)");
        }
    }

    /**
     * Requires branch exists.
     *
     * @param branchId the branch id
     */
    private void requireBranchExists(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchMenuService.class, "requireBranchExists(Long)");
        try {
            if (!branchRepository.existsById(branchId)) {
                throw new IllegalArgumentException("Branch not found.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchMenuService.class,
                    "requireBranchExists(Long)");
        }
    }
}
