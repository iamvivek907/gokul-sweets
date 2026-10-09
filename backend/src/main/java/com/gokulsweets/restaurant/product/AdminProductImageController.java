package com.gokulsweets.restaurant.product;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.product.dto.ProductResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** HTTP endpoints for admin product image operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/products/{id}/image")
public class AdminProductImageController {

    private final ProductService products;

    /**
     * Handles {@code POST /api/admin/products/{id}/image} for admin product image.
     *
     * @param id the id supplied to this method
     * @param image the image supplied to this method
     * @return the value of {@code products.uploadImage(id, image)}
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse upload(@PathVariable Long id, @RequestParam MultipartFile image) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminProductImageController.class, "upload(Long,MultipartFile)");
        try {
            return products.uploadImage(id, image);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminProductImageController.class,
                    "upload(Long,MultipartFile)");
        }
    }

    /**
     * Handles {@code DELETE /api/admin/products/{id}/image} for admin product image.
     *
     * @param id the id supplied to this method
     * @return the value of {@code products.removeImage(id)}
     */
    @DeleteMapping
    public ProductResponse remove(@PathVariable Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminProductImageController.class, "remove(Long)");
        try {
            return products.removeImage(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminProductImageController.class, "remove(Long)");
        }
    }
}
