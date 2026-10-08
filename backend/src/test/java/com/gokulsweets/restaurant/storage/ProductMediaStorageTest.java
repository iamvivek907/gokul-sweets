package com.gokulsweets.restaurant.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.services.s3.S3Client;

class ProductMediaStorageTest {
    @Test
    void replacementsGetDifferentPublicUrls() {
        var service = new R2StorageService(mock(S3Client.class));
        ReflectionTestUtils.setField(service, "bucketName", "test");
        ReflectionTestUtils.setField(service, "publicUrl", "https://media.example.invalid/");
        var image =
                new MockMultipartFile(
                        "image",
                        "sweet.jpg",
                        "image/jpeg",
                        new byte[] {-1, -40, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        String first = service.uploadProductImage(1L, "Kaju Katli", image),
                second = service.uploadProductImage(1L, "Kaju Katli", image);
        assertThat(first)
                .startsWith("https://media.example.invalid/products/kaju-katli-1-")
                .endsWith(".jpg");
        assertThat(second).isNotEqualTo(first);
    }
}
