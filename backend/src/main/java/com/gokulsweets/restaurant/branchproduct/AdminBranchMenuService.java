package com.gokulsweets.restaurant.branchproduct;

import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.branchproduct.dto.AdminBranchProductResponse;
import com.gokulsweets.restaurant.branchproduct.dto.AdminBranchProductUpdateRequest;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBranchMenuService {

    private final BranchRepository branchRepository;

    private final BranchProductRepository
            branchProductRepository;

    private final StaffAuthorizationService
            staffAuthorizationService;


    /*
     * =========================================================
     * GET ADMIN MENU
     * =========================================================
     */

    @Transactional(readOnly = true)
    public List<AdminBranchProductResponse> getBranchMenu(
            Long branchId
    ) {

        validateBranchId(
                branchId
        );


        authorize(
                branchId
        );


        requireBranchExists(
                branchId
        );


        List<BranchProduct> branchProducts =
                branchProductRepository
                        .findAdminMenu(
                                branchId
                        );


        log.debug(
                "Loaded {} admin menu items for branchId={}",
                branchProducts.size(),
                branchId
        );


        return branchProducts
                .stream()
                .map(
                        AdminBranchProductResponse::from
                )
                .toList();
    }


    /*
     * =========================================================
     * UPDATE BRANCH MENU ITEM
     * =========================================================
     */

    @Transactional
    public AdminBranchProductResponse updateBranchProduct(
            Long branchId,
            Long productId,
            AdminBranchProductUpdateRequest request
    ) {

        validateBranchId(
                branchId
        );


        validateProductId(
                productId
        );


        if (
                request
                        == null
        ) {

            throw new IllegalArgumentException(
                    "Update request is required."
            );
        }


        authorize(
                branchId
        );


        requireBranchExists(
                branchId
        );


        BranchProduct branchProduct =
                branchProductRepository
                        .findByBranchIdAndProductId(
                                branchId,
                                productId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Product is not configured for this branch."
                                        )
                        );


        boolean changed =
                false;


        /*
         * -----------------------------------------------------
         * AVAILABILITY
         * -----------------------------------------------------
         */

        if (
                request.available()
                        != null
        ) {

            if (
                    branchProduct.isAvailable()
                            != request.available()
            ) {

                branchProduct.setAvailable(
                        request.available()
                );


                changed =
                        true;
            }
        }


        /*
         * -----------------------------------------------------
         * PRICE OVERRIDE
         * -----------------------------------------------------
         */

        if (
                Boolean.TRUE.equals(
                        request.clearPriceOverride()
                )
        ) {

            if (
                    branchProduct.getPriceOverride()
                            != null
            ) {

                branchProduct.setPriceOverride(
                        null
                );


                changed =
                        true;
            }

        } else if (
                request.priceOverride()
                        != null
        ) {

            if (
                    branchProduct.getPriceOverride()
                            == null
                            ||
                            branchProduct
                                    .getPriceOverride()
                                    .compareTo(
                                            request.priceOverride()
                                    )
                                    != 0
            ) {

                branchProduct.setPriceOverride(
                        request.priceOverride()
                );


                changed =
                        true;
            }
        }


        /*
         * -----------------------------------------------------
         * DISPLAY ORDER
         * -----------------------------------------------------
         */

        if (
                request.displayOrder()
                        != null
        ) {

            if (
                    !request.displayOrder()
                            .equals(
                                    branchProduct
                                            .getDisplayOrder()
                            )
            ) {

                branchProduct.setDisplayOrder(
                        request.displayOrder()
                );


                changed =
                        true;
            }
        }


        if (
                changed
        ) {

            branchProductRepository
                    .save(
                            branchProduct
                    );


            log.info(
                    "Updated branch menu item branchId={}, productId={}",
                    branchId,
                    productId
            );

        } else {

            log.debug(
                    "No branch menu changes detected branchId={}, productId={}",
                    branchId,
                    productId
            );
        }


        return AdminBranchProductResponse
                .from(
                        branchProduct
                );
    }


    /*
     * =========================================================
     * AUTHORIZATION
     * =========================================================
     */

    private void authorize(
            Long branchId
    ) {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.MENU_MANAGE
                );


        staffAuthorizationService
                .requireBranchAccess(
                        branchId
                );
    }


    /*
     * =========================================================
     * VALIDATION
     * =========================================================
     */

    private void validateBranchId(
            Long branchId
    ) {

        if (
                branchId == null
                        ||
                        branchId <= 0
        ) {

            throw new IllegalArgumentException(
                    "Valid branchId is required."
            );
        }
    }


    private void validateProductId(
            Long productId
    ) {

        if (
                productId == null
                        ||
                        productId <= 0
        ) {

            throw new IllegalArgumentException(
                    "Valid productId is required."
            );
        }
    }


    private void requireBranchExists(
            Long branchId
    ) {

        if (
                !branchRepository
                        .existsById(
                                branchId
                        )
        ) {

            throw new IllegalArgumentException(
                    "Branch not found."
            );
        }
    }
}