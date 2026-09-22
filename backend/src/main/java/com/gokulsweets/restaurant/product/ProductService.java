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
        if (
                oldImageUrl != null
                        && !oldImageUrl.isBlank()
                        && !oldImageUrl.equals(
                        newImageUrl
                )
        ) {

            try {

                r2StorageService.deleteProductImage(
                        oldImageUrl
                );

            } catch (Exception exception) {

                log.warn(
                        "Unable to delete previous product image for product {}: {}",
                        productId,
                        oldImageUrl,
                        exception
                );
            }
        }


        return ProductResponse.from(
                saved
        );
    }


    @Transactional
    public ProductResponse removeImage(
            Long productId
    ) {

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
        if (
                imageUrl != null
                        && !imageUrl.isBlank()
        ) {

            try {

                r2StorageService.deleteProductImage(
                        imageUrl
                );

            } catch (Exception exception) {

                /*
                 * Do not remove the database reference if the
                 * R2 object could not be deleted.
                 *
                 * This prevents the database from saying that
                 * an image does not exist when the R2 object
                 * actually still exists.
                 */
                log.error(
                        "Unable to delete product image for product {}",
                        productId,
                        exception
                );

                throw new IllegalStateException(
                        "Unable to remove product image.",
                        exception
                );
            }
        }


        /*
         * R2 deletion succeeded, so clear the database URL.
         */
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