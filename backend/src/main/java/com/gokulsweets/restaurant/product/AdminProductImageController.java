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
     * Uploads the operation.
     *
     * @param id the id
     * @param image the image
     * @return the upload result
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
     * Removes the operation.
     *
     * @param id the id
     * @return the remove result
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
