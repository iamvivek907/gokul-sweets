package com.gokulsweets.restaurant.campaign;

import com.gokulsweets.restaurant.storage.R2StorageService;
import com.gokulsweets.restaurant.config.EnhancementProperties;
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
    private final EnhancementProperties flags = new EnhancementProperties();
    private final CampaignPublicationRepository publications = mock(CampaignPublicationRepository.class);
    private final CampaignService service = new CampaignService(repository, storage, Clock.fixed(now, ZoneOffset.UTC), flags, publications);

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

    @Test void draftDoesNotReplacePublishedSnapshotAndRollbackRejectsAnotherCampaignsRevision() {
        flags.setControlledCampaignPublishing(true);
        var draft = campaign(); draft.setPublishedRevision(41L);
        var live = CampaignPublication.from(draft, now); live.setId(41L);
        when(repository.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(draft));
        when(publications.findById(41L)).thenReturn(Optional.of(live));
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(draft));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.save(1L, new CampaignRequest("HERO", "New draft", null, null, null, null, null, false, 0, "New photo", null));
        assertThat(service.active()).extracting(HomepageCampaign::getTitle).containsExactly("Test");
        assertThat(draft.getPublishedRevision()).isEqualTo(41L);
        live.setCampaignId(2L);
        assertThatThrownBy(() -> service.rollback(1L, 41L)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another campaign");
    }

    @Test void publicationSwitchesOnlyAfterValidSnapshotAndRestoresHistoricalRevision() {
        flags.setControlledCampaignPublishing(true);
        var draft = campaign();
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(draft));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(publications.saveAndFlush(any())).thenAnswer(invocation -> {
            CampaignPublication published = invocation.getArgument(0); published.setId(42L); return published;
        });
        assertThatThrownBy(() -> service.save(1L, request(true))).hasMessageContaining("image description");
        verifyNoInteractions(publications);
        var accepted = new CampaignRequest("HERO", "New version", null, null, null, null, null, true, 0, "Sweets in a box", null);
        service.save(1L, accepted);
        assertThat(draft.getPublishedRevision()).isEqualTo(42L);
        var previous = CampaignPublication.from(draft, now); previous.setId(41L);
        when(publications.findById(41L)).thenReturn(Optional.of(previous));
        service.rollback(1L, 41L);
        assertThat(draft.getPublishedRevision()).isEqualTo(41L);
    }

    @Test void staleEditorCannotOverwriteDraftOrRestorePublication() {
        flags.setControlledCampaignPublishing(true);
        var draft = campaign(); draft.setEditVersion(8);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(draft));
        var newDraft = new CampaignRequest("HERO", "Stale", null, null, null, null, null, false, 0, "Sweets", null);
        assertThatThrownBy(() -> service.save(1L, newDraft, 7L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        assertThatThrownBy(() -> service.rollback(1L, 42L, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(publications);
    }

    private HomepageCampaign campaign() {
        var c = new HomepageCampaign(); c.setId(1L); c.setTitle("Test"); c.setActive(true); c.setMediaUrl("old"); c.setMediaType("image/png");
        return c;
    }
    private CampaignRequest request(boolean active) {
        return new CampaignRequest("HERO", "Test", null, null, null, null, null, active, 0);
    }
}
