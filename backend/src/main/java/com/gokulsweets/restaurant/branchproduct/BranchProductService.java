package com.gokulsweets.restaurant.branchproduct;

import com.gokulsweets.restaurant.branchproduct.dto.BranchProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BranchProductService {

    private static final Logger log =
            LoggerFactory.getLogger(BranchProductService.class);

    private final BranchProductRepository branchProductRepository;

    public BranchProductService(
            BranchProductRepository branchProductRepository
    ) {
        this.branchProductRepository = branchProductRepository;
    }

    @Transactional(readOnly = true)
    public List<BranchProductResponse> getAvailableProducts(
            Long branchId
    ) {

        log.debug(
                "Fetching available products for branchId={}",
                branchId
        );

        List<BranchProductResponse> products =
                branchProductRepository
                        .findByBranchIdAndAvailableTrueOrderByDisplayOrderAsc(
                                branchId
                        )
                        .stream()
                        .map(BranchProductResponse::from)
                        .toList();

        log.debug(
                "Found {} available products for branchId={}",
                products.size(),
                branchId
        );

        return products;
    }

    @Transactional(readOnly = true)
    public BranchProductResponse getBranchProduct(
            Long branchId,
            Long productId
    ) {

        log.debug(
                "Fetching branch product: branchId={}, productId={}",
                branchId,
                productId
        );

        BranchProduct branchProduct =
                branchProductRepository
                        .findByBranchIdAndProductId(
                                branchId,
                                productId
                        )
                        .orElseThrow(() -> {

                            log.warn(
                                    "Branch product not found: branchId={}, productId={}",
                                    branchId,
                                    productId
                            );

                            return new IllegalArgumentException(
                                    "Product not found for branch"
                            );
                        });

        return BranchProductResponse.from(branchProduct);
    }
}