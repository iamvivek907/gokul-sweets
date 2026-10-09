package com.gokulsweets.restaurant.branchproduct;

import com.gokulsweets.restaurant.branchproduct.dto.BranchProductResponse;
import com.gokulsweets.restaurant.observability.MethodTiming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Coordinates branch product operations. */
@Service
public class BranchProductService {

    private static final Logger log = LoggerFactory.getLogger(BranchProductService.class);

    private final BranchProductRepository branchProductRepository;

    /**
     * Creates a branch product service instance.
     *
     * @param branchProductRepository the branch product repository
     */
    public BranchProductService(BranchProductRepository branchProductRepository) {
        this.branchProductRepository = branchProductRepository;
    }

    /**
     * Returns available products.
     *
     * @param branchId the branch id
     * @return the get available products result
     */
    @Transactional(readOnly = true)
    public List<BranchProductResponse> getAvailableProducts(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchProductService.class, "getAvailableProducts(Long)");
        try {
            log.debug("Fetching available products for branchId={}", branchId);
            List<BranchProductResponse> products =
                    branchProductRepository
                            .findByBranchIdAndAvailableTrueOrderByDisplayOrderAsc(branchId)
                            .stream()
                            .map(BranchProductResponse::from)
                            .toList();
            log.debug("Found {} available products for branchId={}", products.size(), branchId);
            return products;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchProductService.class,
                    "getAvailableProducts(Long)");
        }
    }

    /**
     * Returns branch product.
     *
     * @param branchId the branch id
     * @param productId the product id
     * @return the get branch product result
     */
    @Transactional(readOnly = true)
    public BranchProductResponse getBranchProduct(Long branchId, Long productId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchProductService.class, "getBranchProduct(Long,Long)");
        try {
            log.debug("Fetching branch product: branchId={}, productId={}", branchId, productId);
            BranchProduct branchProduct =
                    branchProductRepository
                            .findByBranchIdAndProductId(branchId, productId)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "Branch product not found: branchId={},"
                                                        + " productId={}",
                                                branchId,
                                                productId);
                                        return new IllegalArgumentException(
                                                "Product not found for branch");
                                    });
            return BranchProductResponse.from(branchProduct);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchProductService.class,
                    "getBranchProduct(Long,Long)");
        }
    }
}
