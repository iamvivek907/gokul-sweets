package com.gokulsweets.restaurant.product;

import com.gokulsweets.restaurant.product.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;


    @GetMapping
    public List<ProductResponse> getActiveProducts() {

        return productService
                .getActiveProducts();
    }


    @GetMapping("/{id}")
    public ProductResponse getProduct(
            @PathVariable Long id
    ) {

        return productService
                .getProduct(id);
    }


    @GetMapping("/category/{categoryId}")
    public List<ProductResponse> getProductsByCategory(
            @PathVariable Long categoryId
    ) {

        return productService
                .getProductsByCategory(
                        categoryId
                );
    }


    /*
     * =========================================================
     * ADMIN PRODUCT IMAGE
     * =========================================================
     */

    @PostMapping(
            value = "/{id}/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ProductResponse uploadImage(
            @PathVariable Long id,
            @RequestParam("image")
            MultipartFile image
    ) {

        return productService
                .uploadImage(
                        id,
                        image
                );
    }


    @DeleteMapping("/{id}/image")
    public ProductResponse removeImage(
            @PathVariable Long id
    ) {

        return productService
                .removeImage(id);
    }
}