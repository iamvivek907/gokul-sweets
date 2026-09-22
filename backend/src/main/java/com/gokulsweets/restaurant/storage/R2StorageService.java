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
import java.util.UUID;
import java.nio.charset.StandardCharsets;

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

    public record CampaignMedia(String url, String contentType) {}

    public CampaignMedia uploadCampaignMedia(Long campaignId, MultipartFile file, boolean staticOnly) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("Choose a campaign file of 5 MB or smaller.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        Set<String> allowed = staticOnly ? ALLOWED_CONTENT_TYPES
                : Set.of("image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/webm");
        if (!allowed.contains(type)) throw new IllegalArgumentException("Unsupported campaign media type.");
        try {
            byte[] bytes = file.getBytes();
            if (!matchesSignature(type, bytes)) throw new IllegalArgumentException("File content does not match its media type.");
            if ((type.equals("image/png") || type.equals("image/webp"))
                    && hasAnimationChunk(type, bytes)) {
                throw new IllegalArgumentException("Animated PNG/WebP is not supported. Use GIF/video with a static fallback.");
            }
            String extension = switch (type) {
                case "image/gif" -> ".gif";
                case "video/mp4" -> ".mp4";
                case "video/webm" -> ".webm";
                default -> getExtension(type);
            };
            String key = "campaigns/" + campaignId + "/" + UUID.randomUUID() + extension;
            r2Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).contentType(type)
                            .contentLength(file.getSize()).cacheControl("public, max-age=31536000, immutable").build(),
                    RequestBody.fromBytes(bytes));
            return new CampaignMedia(buildPublicUrl(key), type);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read campaign media.", exception);
        }
    }

    static boolean matchesSignature(String type, byte[] bytes) {
        if (bytes.length < 12) return false;
        String prefix = new String(bytes, 0, 12, StandardCharsets.ISO_8859_1);
        return switch (type) {
            case "image/jpeg" -> (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
            case "image/png" -> prefix.startsWith("\u0089PNG\r\n\u001a\n");
            case "image/webp" -> prefix.startsWith("RIFF") && prefix.substring(8).equals("WEBP");
            case "image/gif" -> prefix.startsWith("GIF87a") || prefix.startsWith("GIF89a");
            case "video/mp4" -> prefix.substring(4, 8).equals("ftyp");
            case "video/webm" -> (bytes[0] & 255) == 0x1A && (bytes[1] & 255) == 0x45
                    && (bytes[2] & 255) == 0xDF && (bytes[3] & 255) == 0xA3;
            default -> false;
        };
    }

    static boolean hasAnimationChunk(String type, byte[] bytes) {
        // Reject animation chunks so a file advertised as a static fallback cannot ignore reduced-motion preferences.
        boolean png = type.equals("image/png");
        var buffer = java.nio.ByteBuffer.wrap(bytes).order(png
                ? java.nio.ByteOrder.BIG_ENDIAN : java.nio.ByteOrder.LITTLE_ENDIAN);
        long offset = png ? 8 : 12;
        while (offset + 8 <= bytes.length) {
            int position = (int) offset;
            String chunk = new String(bytes, position + (png ? 4 : 0), 4, StandardCharsets.US_ASCII);
            if (chunk.equals(png ? "acTL" : "ANIM")) return true;
            long length = Integer.toUnsignedLong(buffer.getInt(position + (png ? 0 : 4)));
            offset += (png ? 12 : 8) + length + (!png ? length % 2 : 0);
        }
        return false;
    }

    public void deleteCampaignMedia(String url) {
        String key = extractKeyFromPublicUrl(url);
        if (key == null || !key.startsWith("campaigns/") || key.contains("..")) {
            throw new IllegalArgumentException("Only managed campaign media can be removed.");
        }
        r2Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    }

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