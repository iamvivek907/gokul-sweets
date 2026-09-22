package com.gokulsweets.restaurant.branchproduct;

import com.gokulsweets.restaurant.branchproduct.dto.BranchProductResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches/{branchId}/products")
public class BranchProductController {

    private final BranchProductService branchProductService;

    public BranchProductController(
            BranchProductService branchProductService
    ) {
        this.branchProductService = branchProductService;
    }

    @GetMapping
    public List<BranchProductResponse> getAvailableProducts(
            @PathVariable Long branchId
    ) {

        return branchProductService
                .getAvailableProducts(branchId);
    }

    @GetMapping("/{productId}")
    public BranchProductResponse getBranchProduct(
            @PathVariable Long branchId,
            @PathVariable Long productId
    ) {

        return branchProductService
                .getBranchProduct(
                        branchId,
                        productId
                );
    }
}