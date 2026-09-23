package com.gokulsweets.restaurant.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampaignStorageTest {
    private final S3Client client = mock(S3Client.class);
    private final R2StorageService storage = new R2StorageService(client);

    @Test void replacementUsesDistinctManagedKeysAndExistingBucket() {
        ReflectionTestUtils.setField(storage, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(storage, "publicUrl", "https://example.invalid");
        byte[] image = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jBuoAAAAASUVORK5CYII=");
        var file = new MockMultipartFile("file", "static.png", "image/png", image);
        var first = storage.uploadCampaignMedia(7L, file, true);
        var replacement = storage.uploadCampaignMedia(7L, file, true);
        assertThat(first.url()).startsWith("https://example.invalid/campaigns/7/").endsWith(".png");
        assertThat(replacement.url()).isNotEqualTo(first.url());
        var requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client, times(2)).putObject(requests.capture(), any(RequestBody.class));
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.bucket()).isEqualTo("test-bucket");
            assertThat(request.contentType()).isEqualTo("image/png");
            assertThat(request.contentLength()).isEqualTo(image.length);
            assertThat(request.cacheControl()).contains("immutable");
        });
    }

    @Test void rejectsOversizeUnsupportedAndMislabeledFilesWithoutUploading() {
        assertThatThrownBy(() -> storage.uploadCampaignMedia(1L,
                new MockMultipartFile("file", "big.gif", "image/gif", new byte[5 * 1024 * 1024 + 1]), false))
                .hasMessageContaining("5 MB");
        assertThatThrownBy(() -> storage.uploadCampaignMedia(1L,
                new MockMultipartFile("file", "bad.svg", "image/svg+xml", "<svg>hello</svg>".getBytes()), false))
                .hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> storage.uploadCampaignMedia(1L,
                new MockMultipartFile("file", "fake.png", "image/png", "not a real PNG image".getBytes()), false))
                .hasMessageContaining("does not match");
        verifyNoInteractions(client);
    }

    @Test void fallbackRejectsAnimationAndManagedDeletionCannotTouchProductAssets() {
        assertThatThrownBy(() -> storage.uploadCampaignMedia(1L,
                new MockMultipartFile("file", "animation.gif", "image/gif", "GIF89a1234567".getBytes()), true))
                .hasMessageContaining("Unsupported");
        ReflectionTestUtils.setField(storage, "publicUrl", "https://example.invalid");
        assertThatThrownBy(() -> storage.deleteCampaignMedia("https://example.invalid/products/1.png"))
                .hasMessageContaining("Only managed");
        assertThatThrownBy(() -> storage.deleteCampaignMedia("https://outside.invalid/campaigns/1.png"))
                .hasMessageContaining("Only managed");
        verifyNoInteractions(client);
    }

    @Test void knownSignaturesAndAnimationMarkersAreRecognized() {
        assertThat(R2StorageService.matchesSignature("image/gif", "GIF89a1234567".getBytes())).isTrue();
        assertThat(R2StorageService.matchesSignature("video/mp4", "1234ftypisom".getBytes())).isTrue();
        assertThat(R2StorageService.matchesSignature("image/png", "\u0089PNG\r\n\u001a\n1234".getBytes(StandardCharsets.ISO_8859_1))).isTrue();
        assertThat(R2StorageService.hasAnimationChunk("image/webp", "RIFF1234WEBPANIM0000".getBytes())).isTrue();
    }
}
