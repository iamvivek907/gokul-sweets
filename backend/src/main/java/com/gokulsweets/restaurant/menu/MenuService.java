package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.branchproduct.BranchProductRepository;
import com.gokulsweets.restaurant.category.Category;
import com.gokulsweets.restaurant.menu.dto.MenuCategoryResponse;
import com.gokulsweets.restaurant.menu.dto.MenuProductResponse;
import com.gokulsweets.restaurant.product.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    private final BranchRepository branchRepository;

    private final BranchProductRepository branchProductRepository;


    @Transactional(readOnly = true)
    public List<MenuCategoryResponse> getMenu(
            Long branchId
    ) {

        Branch branch =
                validateBranch(
                        branchId
                );

        List<BranchProduct> branchProducts =
                branchProductRepository
                        .findAvailableMenu(
                                branch.getId()
                        );

        Map<Long, CategoryBucket> categories =
                new LinkedHashMap<>();


        for (
                BranchProduct branchProduct :
                branchProducts
        ) {

            Product product =
                    branchProduct.getProduct();

            Category category =
                    product.getCategory();

            CategoryBucket bucket =
                    categories.computeIfAbsent(
                            category.getId(),
                            ignored ->
                                    new CategoryBucket(
                                            category
                                    )
                    );


            bucket.products().add(
                    toMenuProduct(
                            branchProduct,
                            product,
                            category
                    )
            );
        }


        List<MenuCategoryResponse> response =
                categories.values()
                        .stream()
                        .map(
                                bucket ->
                                        new MenuCategoryResponse(
                                                bucket.category().getId(),
                                                bucket.category().getName(),
                                                bucket.category().getDescription(),
                                                bucket.category().getDisplayOrder(),
                                                List.copyOf(
                                                        bucket.products()
                                                )
                                        )
                        )
                        .toList();


        log.debug(
                "Loaded customer menu: branchId={}, categoryCount={}, productCount={}",
                branch.getId(),
                response.size(),
                branchProducts.size()
        );


        return response;
    }


    private Branch validateBranch(
            Long branchId
    ) {

        if (branchId == null) {

            throw new IllegalArgumentException(
                    "Branch ID is required."
            );
        }


        Branch branch =
                branchRepository
                        .findById(branchId)
                        .orElseThrow(
                                () -> {

                                    log.warn(
                                            "Menu requested for unknown branch: branchId={}",
                                            branchId
                                    );

                                    return new IllegalArgumentException(
                                            "Selected branch does not exist."
                                    );
                                }
                        );


        if (!branch.isActive()) {

            log.warn(
                    "Menu requested for inactive branch: branchId={}",
                    branchId
            );

            throw new IllegalArgumentException(
                    "Selected branch is currently unavailable."
            );
        }


        return branch;
    }


    private MenuProductResponse toMenuProduct(
            BranchProduct branchProduct,
            Product product,
            Category category
    ) {

        BigDecimal effectivePrice =
                branchProduct.getPriceOverride() != null
                        ? branchProduct.getPriceOverride()
                        : product.getBasePrice();


        return new MenuProductResponse(

                product.getId(),

                category.getId(),

                category.getName(),

                product.getName(),

                product.getDescription(),

                effectivePrice,

                /*
                 * Cloudflare R2 public URL.
                 */
                product.getImageUrl(),

                branchProduct.isAvailable(),

                product.getSaleMode(),

                product.getMinimumWeightGrams(),

                product.getWeightStepGrams()
        );
    }


    private record CategoryBucket(
            Category category,
            List<MenuProductResponse> products
    ) {

        private CategoryBucket(
                Category category
        ) {

            this(
                    category,
                    new ArrayList<>()
            );
        }
    }
}