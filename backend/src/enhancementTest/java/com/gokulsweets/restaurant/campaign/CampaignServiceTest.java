package com.gokulsweets.restaurant.campaign;

import com.gokulsweets.restaurant.storage.R2StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampaignServiceTest {
    private final HomepageCampaignRepository repository = mock(HomepageCampaignRepository.class);
    private final R2StorageService storage = mock(R2StorageService.class);
    private final Instant now = Instant.parse("2026-10-01T00:00:00Z");
    private final CampaignService service = new CampaignService(repository, storage, Clock.fixed(now, ZoneOffset.UTC));

    @AfterEach void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void campaignStartsInclusivelyAndExpiresExclusively() {
        var c = campaign(); c.setStartAt(now); c.setEndAt(now.plusSeconds(60));
        assertThat(c.visibleAt(now.minusSeconds(1))).isFalse();
        assertThat(c.visibleAt(now)).isTrue();
        assertThat(c.visibleAt(now.plusSeconds(60))).isFalse();
        c.setActive(false); assertThat(c.visibleAt(now)).isFalse();
    }

    @Test void expiredCampaignFallsThroughToNextActiveOne() {
        var expired = campaign(); expired.setEndAt(now);
        var next = campaign(); next.setId(2L);
        when(repository.findByActiveTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(expired, next));
        assertThat(service.active()).containsExactly(next);
    }

    @Test void activationRequiresStaticFallbackForAnimation() {
        var c = campaign(); c.setMediaType("video/mp4");
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.save(1L, request(true))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("static fallback");
        verify(repository, never()).save(any());
    }

    @Test void replacementDeletesOldObjectOnlyAfterCommitAndRollbackDeletesOnlyNewObject() {
        var c = campaign();
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(c));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var file = new MockMultipartFile("file", "image.png", "image/png", new byte[12]);
        when(storage.uploadCampaignMedia(1L, file, false)).thenReturn(new R2StorageService.CampaignMedia("new", "image/png"));
        TransactionSynchronizationManager.initSynchronization();
        service.upload(1L, file, false);
        verify(storage, never()).deleteCampaignMedia(any());
        var sync = TransactionSynchronizationManager.getSynchronizations().get(0);
        sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage).deleteCampaignMedia("old");
        clearInvocations(storage);
        sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).deleteCampaignMedia("new");
        verify(storage, never()).deleteCampaignMedia("old");
    }

    @Test void removalDeactivatesWithoutChangingOtherMedia() {
        var c = campaign(); c.setFallbackMediaUrl("poster");
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(c));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        service.removeMedia(1L, false);
        assertThat(c.isActive()).isFalse(); assertThat(c.getMediaUrl()).isNull();
        assertThat(c.getFallbackMediaUrl()).isEqualTo("poster");
        verifyNoInteractions(storage);
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        verify(storage).deleteCampaignMedia("old");
    }

    @Test void invalidScheduleAndPartialCtaAreRejected() {
        assertThatThrownBy(() -> new CampaignRequest("HERO", "Test", null, null, null, now, now, false, 0).validate())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampaignRequest("HERO", "Test", null, "Click", null, null, null, false, 0).validate())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void uploadRetryAndMetadataSaveDoNotRepeatStorageOperations() {
        var c = campaign();
        var key = UUID.randomUUID();
        var storedHash = new java.util.concurrent.atomic.AtomicReference<String>();
        when(repository.mediaRequestHash(1L, false, key)).thenAnswer(ignored -> Optional.ofNullable(storedHash.get()));
        doAnswer(invocation -> {storedHash.set(invocation.getArgument(3)); return null;})
                .when(repository).recordMediaRequest(eq(1L), eq(false), eq(key), anyString());
        var file = new MockMultipartFile("file", "image.png", "image/png", new byte[12]);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(c));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.uploadCampaignMedia(1L, file, false)).thenReturn(new R2StorageService.CampaignMedia("new", "image/png"));
        TransactionSynchronizationManager.initSynchronization();
        service.upload(1L, file, false, key);
        service.upload(1L, file, false, key);
        service.save(1L, request(false));
        service.save(1L, request(true));
        verify(storage, times(1)).uploadCampaignMedia(1L, file, false);
        verifyNoMoreInteractions(storage);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test void repeatedCreationKeyReturnsExistingDraftWithoutAnotherSave() {
        var c = campaign(); var key = UUID.randomUUID();
        when(repository.createDraft(eq(key), anyString(), eq("Test"), eq("HERO"))).thenAnswer(invocation -> {
            c.setCreationRequestHash(invocation.getArgument(1)); return 0;
        });
        when(repository.findByCreationRequestId(key)).thenReturn(Optional.of(c));
        assertThat(service.create(request(false), key)).isSameAs(c);
        verify(repository, never()).save(any());
        verifyNoInteractions(storage);
    }

    private HomepageCampaign campaign() {
        var c = new HomepageCampaign(); c.setId(1L); c.setTitle("Test"); c.setActive(true); c.setMediaUrl("old"); c.setMediaType("image/png");
        return c;
    }
    private CampaignRequest request(boolean active) {
        return new CampaignRequest("HERO", "Test", null, null, null, null, null, active, 0);
    }
}
