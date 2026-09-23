package com.gokulsweets.restaurant.campaign;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.core.sync.RequestBody;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "REVISION_DB_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55489/gokul_enhancements_revision",
        "spring.datasource.username=gokul_test", "spring.datasource.password=test-only",
        "inventory.automation.scheduler-enabled=false", "cloudflare.r2.account-id=validation-only",
        "cloudflare.r2.access-key-id=validation-only", "cloudflare.r2.secret-access-key=validation-only"
})
class CampaignConcurrencyTest {
    @Autowired CampaignService service;
    @MockitoBean S3Client storage;

    @Test void concurrentRetriesPayloadConflictsAndScopedKeysUseOnlyNecessaryPuts() throws Exception {
        var request = new CampaignRequest("HERO", "Retry integration", null, null, null, null, null, false, 0);
        var createKey = UUID.randomUUID();
        List<HomepageCampaign> created;
        try (var pool = Executors.newFixedThreadPool(8)) {
            created = pool.invokeAll(java.util.stream.IntStream.range(0, 8)
                    .<Callable<HomepageCampaign>>mapToObj(i -> () -> service.create(request, createKey)).toList())
                    .stream().map(future -> {
                        try {return future.get();} catch (Exception exception) {throw new RuntimeException(exception);}
                    }).toList();
        }
        assertThat(created.stream().map(HomepageCampaign::getId).distinct()).hasSize(1);
        assertThatThrownBy(() -> service.create(new CampaignRequest("HERO", "Different", null, null, null, null, null, false, 0), createKey))
                .hasMessageContaining("different fields");
        var id = created.getFirst().getId();
        byte[] bytes = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jBuoAAAAASUVORK5CYII=");
        var file = new MockMultipartFile("file", "test.png", "image/png", bytes);
        var key = UUID.randomUUID();
        when(storage.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (var future : pool.invokeAll(java.util.stream.IntStream.range(0, 8)
                    .<Callable<HomepageCampaign>>mapToObj(i -> () -> service.upload(id, file, false, key)).toList())) future.get();
        }
        verify(storage, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        service.save(id, request);
        verify(storage, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        assertThatThrownBy(() -> service.upload(id, new MockMultipartFile("file", "other.png", "image/png", new byte[12]), false, key))
                .hasMessageContaining("different file");
        service.upload(id, file, true, key); // Same key in the other media role is a distinct operation.
        var other = service.create(request, UUID.randomUUID());
        service.upload(other.getId(), file, false, key);
        verify(storage, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        var retryKey = UUID.randomUUID();
        when(storage.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new IllegalStateException("Synthetic PUT failure")).thenReturn(PutObjectResponse.builder().build());
        assertThatThrownBy(() -> service.upload(id, file, false, retryKey)).hasMessageContaining("Synthetic");
        service.upload(id, file, false, retryKey);
        service.upload(id, file, false, retryKey);
        verify(storage, times(5)).putObject(any(PutObjectRequest.class), any(RequestBody.class)); // Four successful PUTs; one failed attempt.
        assertThatThrownBy(() -> service.upload(id, file, false, key)).hasMessageContaining("replaced");
    }
}
