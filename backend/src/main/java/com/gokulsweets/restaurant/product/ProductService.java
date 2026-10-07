package com.gokulsweets.restaurant.product;

import com.gokulsweets.restaurant.product.dto.ProductResponse;
import com.gokulsweets.restaurant.storage.R2StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    private final R2StorageService r2StorageService;
    private final com.gokulsweets.restaurant.security.StaffAuthorizationService authorization;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void authorizeImage(Long productId) {
        authorization.requirePermission(com.gokulsweets.restaurant.staff.PermissionName.MENU_MANAGE);
        var branches = jdbc.queryForList("SELECT branch_id FROM branch_products WHERE product_id=?", Long.class, productId);
        if (branches.isEmpty()) throw new org.springframework.security.access.AccessDeniedException("Product must be assigned to a permitted branch.");
        branches.forEach(authorization::requireBranchAccess);
    }
    private void deleteAfterCommit(String url) {
        if (url == null || url.isBlank()) return;
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { r2StorageService.deleteProductImage(url); }
                    catch (Exception e) { log.warn("Unable to clean up replaced product media", e); }
                }
            });
    }



    @Transactional(readOnly = true)
    public List<ProductResponse> getActiveProducts() {

        return productRepository
                .findByActiveTrue()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }


    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCategory(
            Long categoryId
    ) {

        return productRepository
                .findByCategoryIdAndActiveTrue(
                        categoryId
                )
                .stream()
                .map(ProductResponse::from)
                .toList();
    }


    @Transactional(readOnly = true)
    public ProductResponse getProduct(
            Long id
    ) {

        Product product =
                productRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Product not found: "
                                                        + id
                                        )
                        );

        return ProductResponse.from(product);
    }


    @Transactional
    public ProductResponse uploadImage(
            Long productId,
            MultipartFile image
    ) {

        authorizeImage(productId);

        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Product not found: "
                                                        + productId
                                        )
                        );


        /*
         * Remember the currently stored R2 object.
         *
         * This allows us to remove the previous image after
         * the new image has been uploaded successfully.
         */
        String oldImageUrl =
                product.getImageUrl();


        /*
         * Upload the new image.
         *
         * The product name is passed to R2StorageService so
         * the R2 object gets a readable name such as:
         *
         * products/kaju-katli-1.jpg
         */
        String newImageUrl =
                r2StorageService.uploadProductImage(
                        productId,
                        product.getName(),
                        image
                );


        /*
         * Store the new public URL in PostgreSQL.
         */
        product.setImageUrl(
                newImageUrl
        );


        Product saved =
                productRepository.save(
                        product
                );


        /*
         * Remove the previous R2 object only after the new
         * image has been successfully uploaded and the
         * database has been updated.
         *
         * DeleteObject is not a Class A operation.
         *
         * If cleanup fails, we don't want to tell the admin
         * that the upload failed because the new image and
         * database record are already valid.
         */
        if (!newImageUrl.equals(oldImageUrl)) deleteAfterCommit(oldImageUrl);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try { r2StorageService.deleteProductImage(newImageUrl); }
                        catch (Exception e) { log.warn("Unable to clean up rolled-back product media", e); }
                    }
                }
            });

        return ProductResponse.from(
                saved
        );
    }


    @Transactional
    public ProductResponse removeImage(
            Long productId
    ) {

        authorizeImage(productId);

        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Product not found: "
                                                        + productId
                                        )
                        );


        String imageUrl =
                product.getImageUrl();


        /*
         * If there is no image, there is nothing to delete.
         *
         * This avoids any unnecessary R2 API call.
         */
        deleteAfterCommit(imageUrl);

        product.setImageUrl(
                null
        );


        Product saved =
                productRepository.save(
                        product
                );


        return ProductResponse.from(
                saved
        );
    }
}