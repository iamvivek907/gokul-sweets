package com.gokulsweets.restaurant.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class R2StorageService {

    private static final long MAX_IMAGE_SIZE =
            5L * 1024L * 1024L;

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );

    private final S3Client r2Client;

    @Value("${cloudflare.r2.bucket-name}")
    private String bucketName;

    @Value("${cloudflare.r2.public-url}")
    private String publicUrl;


    public String uploadProductImage(
            Long productId,
            String productName,
            MultipartFile file
    ) {

        validateImage(file);

        String extension =
                getExtension(
                        file.getContentType()
                );

        String slug =
                createSlug(productName);

        String key =
                "products/"
                        + slug
                        + "-"
                        + productId
                        + extension;

        try {

            PutObjectRequest request =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(
                                    file.getContentType()
                            )
                            .contentLength(
                                    file.getSize()
                            )
                            .cacheControl(
                                    "public, max-age=31536000"
                            )
                            .build();

            r2Client.putObject(
                    request,
                    RequestBody.fromBytes(
                            file.getBytes()
                    )
            );

            return buildPublicUrl(
                    key
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Unable to read product image.",
                    exception
            );
        }
    }


    /**
     * Deletes the image represented by the stored public URL.
     *
     * This is preferable to trying all possible extensions because
     * the database already knows exactly which image is being used.
     */
    public void deleteProductImage(
            String imageUrl
    ) {

        if (
                imageUrl == null
                        || imageUrl.isBlank()
        ) {
            return;
        }

        String key =
                extractKeyFromPublicUrl(
                        imageUrl
                );

        if (
                key == null
                        || key.isBlank()
        ) {
            return;
        }

        try {

            r2Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build()
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to delete product image.",
                    exception
            );
        }
    }


    private void validateImage(
            MultipartFile file
    ) {

        if (
                file == null
                        || file.isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "Product image is required."
            );
        }

        if (
                file.getSize()
                        > MAX_IMAGE_SIZE
        ) {

            throw new IllegalArgumentException(
                    "Product image must be 5 MB or smaller."
            );
        }

        String contentType =
                file.getContentType();

        if (
                contentType == null
                        || !ALLOWED_CONTENT_TYPES.contains(
                        contentType.toLowerCase(
                                Locale.ROOT
                        )
                )
        ) {

            throw new IllegalArgumentException(
                    "Only JPG, PNG and WebP images are allowed."
            );
        }
    }


    private String getExtension(
            String contentType
    ) {

        if (contentType == null) {

            throw new IllegalArgumentException(
                    "Image content type is required."
            );
        }

        return switch (
                contentType.toLowerCase(
                        Locale.ROOT
                )
                ) {

            case "image/jpeg" ->
                    ".jpg";

            case "image/png" ->
                    ".png";

            case "image/webp" ->
                    ".webp";

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported image format."
                    );
        };
    }


    /**
     * Converts a product name into a safe R2 object-name segment.
     *
     * Examples:
     *
     * "Kaju Katli"       -> "kaju-katli"
     * "Mysore Pak"       -> "mysore-pak"
     * "Gulab Jamun"      -> "gulab-jamun"
     * "Paneer Tikka 2"   -> "paneer-tikka-2"
     */
    private String createSlug(
            String productName
    ) {

        if (
                productName == null
                        || productName.isBlank()
        ) {
            return "product";
        }

        String normalized =
                Normalizer.normalize(
                        productName,
                        Normalizer.Form.NFD
                );

        String slug =
                normalized
                        .replaceAll(
                                "\\p{M}+",
                                ""
                        )
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .replaceAll(
                                "[^\\p{L}\\p{N}]+",
                                "-"
                        )
                        .replaceAll(
                                "^-+|-+$",
                                ""
                        );

        if (slug.isBlank()) {
            return "product";
        }

        return slug;
    }


    private String buildPublicUrl(
            String key
    ) {

        return publicUrl
                .replaceAll("/+$", "")
                + "/"
                + key;
    }


    private String extractKeyFromPublicUrl(
            String imageUrl
    ) {

        String baseUrl =
                publicUrl.replaceAll(
                        "/+$",
                        ""
                );

        String prefix =
                baseUrl + "/";

        if (
                imageUrl.startsWith(
                        prefix
                )
        ) {

            return imageUrl.substring(
                    prefix.length()
            );
        }

        return null;
    }
}