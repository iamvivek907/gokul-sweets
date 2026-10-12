package com.gokulsweets.restaurant.product;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.product.dto.ProductResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** HTTP endpoints for product operations. */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Returns active products.
     *
     * @return the get active products result
     */
    @GetMapping
    public List<ProductResponse> getActiveProducts() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ProductController.class, "getActiveProducts()");
        try {
            return productService.getActiveProducts();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, ProductController.class, "getActiveProducts()");
        }
    }

    /**
     * Returns product.
     *
     * @param id the id
     * @return the get product result
     */
    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ProductController.class, "getProduct(Long)");
        try {
            return productService.getProduct(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, ProductController.class, "getProduct(Long)");
        }
    }

    /**
     * Returns products by category.
     *
     * @param categoryId the category id
     * @return the get products by category result
     */
    @GetMapping("/category/{categoryId}")
    public List<ProductResponse> getProductsByCategory(@PathVariable Long categoryId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ProductController.class, "getProductsByCategory(Long)");
        try {
            return productService.getProductsByCategory(categoryId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductController.class,
                    "getProductsByCategory(Long)");
        }
    }

    /*
     * =========================================================
     * ADMIN PRODUCT IMAGE
     * =========================================================
     */
    /**
     * Uploads image.
     *
     * @param id the id
     * @param image the image
     * @return the upload image result
     */
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse uploadImage(
            @PathVariable Long id, @RequestParam("image") MultipartFile image) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ProductController.class, "uploadImage(Long,MultipartFile)");
        try {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Use the secure admin image endpoint.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductController.class,
                    "uploadImage(Long,MultipartFile)");
        }
    }

    /**
     * Removes image.
     *
     * @param id the id
     * @return the remove image result
     */
    @DeleteMapping("/{id}/image")
    public ProductResponse removeImage(@PathVariable Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ProductController.class, "removeImage(Long)");
        try {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Use the secure admin image endpoint.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, ProductController.class, "removeImage(Long)");
        }
    }
}
