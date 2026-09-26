package com.gokulsweets.restaurant.campaign;

import com.gokulsweets.restaurant.storage.R2StorageService;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {
    private final HomepageCampaignRepository repository;
    private final R2StorageService storage;
    private final Clock inventoryClock;
    private final EnhancementProperties features;
    private final CampaignPublicationRepository publications;

    @Transactional
    public HomepageCampaign create(CampaignRequest request, UUID requestId) {
        if (requestId == null) return save(null, request);
        request.validate();
        var canonical = new StringBuilder();
        for (Object field : new Object[]{request.type(), request.title(), request.subtitle(), request.ctaLabel(),
                request.ctaTarget(), request.startAt(), request.endAt(), request.active(), request.displayOrder(),
                request.altText(), request.branchId()}) {
            String value = field == null ? null : field.toString();
            canonical.append(value == null ? -1 : value.length()).append(':').append(value == null ? "" : value);
        }
        String hash = hash(canonical.toString().getBytes(StandardCharsets.UTF_8));
        int inserted = repository.createDraft(requestId, hash, request.title().trim(), request.type());
        var campaign = repository.findByCreationRequestId(requestId).orElseThrow();
        if (!hash.equals(campaign.getCreationRequestHash())) throw new IllegalArgumentException("This draft request was already used with different fields. Reopen the saved draft before editing.");
        return inserted == 0 ? campaign : save(campaign.getId(), request);
    }

    @Transactional(readOnly = true)
    public List<HomepageCampaign> active(Long branchId) {
        if (features.isControlledCampaignPublishing()) {
            return repository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                    .filter(c -> c.getPublishedRevision() != null)
                    .map(c -> publications.findById(c.getPublishedRevision()).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .map(CampaignPublication::asCampaign)
                    .filter(c -> (c.getBranchId() == null || c.getBranchId().equals(branchId)) && c.visibleAt(inventoryClock.instant()))
                    .sorted(java.util.Comparator.comparingInt(HomepageCampaign::getDisplayOrder).thenComparing(HomepageCampaign::getId))
                    .toList();
        }
        return repository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .filter(campaign -> campaign.visibleAt(inventoryClock.instant())).toList();
    }

    public List<HomepageCampaign> active() { return active(null); }

    @Transactional
    public HomepageCampaign save(Long id, CampaignRequest request) {
        request.validate();
        HomepageCampaign campaign = id == null ? new HomepageCampaign() : require(id);
        campaign.setType(request.type());
        campaign.setTitle(request.title().trim());
        campaign.setSubtitle(request.subtitle());
        campaign.setCtaLabel(request.ctaLabel());
        campaign.setCtaTarget(request.ctaTarget());
        campaign.setStartAt(request.startAt());
        campaign.setEndAt(request.endAt());
        campaign.setDisplayOrder(request.displayOrder());
        campaign.setActive(request.active());
        if (features.isControlledCampaignPublishing()) {
            campaign.setAltText(request.altText() == null ? null : request.altText().trim());
            campaign.setBranchId(request.branchId());
        }
        validateActiveMedia(campaign);
        if (features.isControlledCampaignPublishing() && request.active()) {
            if (campaign.getAltText() == null || campaign.getAltText().isBlank())
                throw new IllegalArgumentException("Add image description before publishing.");
            campaign = repository.saveAndFlush(campaign);
            var publication = publications.saveAndFlush(CampaignPublication.from(campaign, inventoryClock.instant()));
            campaign.setPublishedRevision(publication.getId());
            return repository.saveAndFlush(campaign);
        }
        return features.isControlledCampaignPublishing() ? repository.saveAndFlush(campaign) : repository.save(campaign);
    }

    @Transactional
    public HomepageCampaign save(Long id, CampaignRequest request, Long expectedVersion) {
        if (features.isControlledCampaignPublishing()) checkVersion(id, expectedVersion);
        return save(id, request);
    }

    private void checkVersion(Long id, Long expected) {
        var current = require(id);
        if (expected == null || expected != current.getEditVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Campaign changed in another editor. Reload the draft before saving.");
        }
    }

    @Transactional(readOnly = true)
    public List<CampaignPublication> history(Long id) {
        if (!features.isControlledCampaignPublishing()) throw new IllegalArgumentException("Controlled publishing is disabled.");
        if (!repository.existsById(id)) throw new IllegalArgumentException("Campaign not found.");
        return publications.findByCampaignIdOrderByIdDesc(id);
    }

    @Transactional
    public HomepageCampaign rollback(Long id, Long revision) {
        if (!features.isControlledCampaignPublishing()) throw new IllegalArgumentException("Controlled publishing is disabled.");
        var campaign = require(id);
        var previous = publications.findById(revision).orElseThrow(() -> new IllegalArgumentException("Revision not found."));
        if (!previous.getCampaignId().equals(id)) throw new IllegalArgumentException("Revision belongs to another campaign.");
        campaign.setPublishedRevision(revision);
        return repository.saveAndFlush(campaign);
    }

    @Transactional
    public HomepageCampaign rollback(Long id, Long revision, Long expectedVersion) {
        if (features.isControlledCampaignPublishing()) checkVersion(id, expectedVersion);
        return rollback(id, revision);
    }

    @Transactional
    public HomepageCampaign upload(Long id, MultipartFile file, boolean fallback) {
        return upload(id, file, fallback, null);
    }

    @Transactional
    public HomepageCampaign upload(Long id, MultipartFile file, boolean fallback, UUID requestId) {
        HomepageCampaign campaign = require(id);
        String requestHash = null;
        if (requestId != null) {
            if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) throw new IllegalArgumentException("Choose a campaign file of 5 MB or smaller.");
            try {requestHash = hash((file.getContentType() + ":" + hash(file.getBytes())).getBytes(StandardCharsets.UTF_8));}
            catch (IOException exception) {throw new IllegalStateException("Unable to read campaign media.", exception);}
            var previousHash = repository.mediaRequestHash(id, fallback, requestId);
            if (previousHash.isPresent()) {
                if (!previousHash.get().equals(requestHash)) throw new IllegalArgumentException("This upload request was already used for a different file.");
                if (!requestId.equals(fallback ? campaign.getFallbackRequestId() : campaign.getMediaRequestId()))
                    throw new IllegalArgumentException("This upload has since been replaced or removed. Reload the campaign before changing media.");
                return campaign;
            }
        }
        String oldUrl = fallback ? campaign.getFallbackMediaUrl() : campaign.getMediaUrl();
        var media = storage.uploadCampaignMedia(id, file, fallback);
        // The database and object storage do not share a transaction: keep old media until commit.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) cleanup(media.url());
                else if (!features.isControlledCampaignPublishing() && campaign.getPublishedRevision() == null) cleanup(oldUrl);
            }
        });
        if (fallback) {campaign.setFallbackMediaUrl(media.url()); campaign.setFallbackRequestId(requestId);}
        else {
            campaign.setMediaUrl(media.url());
            campaign.setMediaType(media.contentType());
            campaign.setMediaRequestId(requestId);
        }
        validateActiveMedia(campaign);
        if (requestId != null) repository.recordMediaRequest(id, fallback, requestId, requestHash);
        return features.isControlledCampaignPublishing() ? repository.saveAndFlush(campaign) : repository.save(campaign);
    }

    @Transactional
    public HomepageCampaign upload(Long id, MultipartFile file, boolean fallback, UUID requestId, Long expectedVersion) {
        if (features.isControlledCampaignPublishing()) checkVersion(id, expectedVersion);
        return upload(id, file, fallback, requestId);
    }

    @Transactional
    public HomepageCampaign removeMedia(Long id, boolean fallback) {
        HomepageCampaign campaign = require(id);
        String oldUrl = fallback ? campaign.getFallbackMediaUrl() : campaign.getMediaUrl();
        if (fallback) {
            campaign.setFallbackMediaUrl(null);
            campaign.setFallbackRequestId(null);
            if (animated(campaign)) campaign.setActive(false);
        } else {
            campaign.setMediaUrl(null); campaign.setMediaType(null); campaign.setActive(false);
            campaign.setMediaRequestId(null);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { if (!features.isControlledCampaignPublishing() && campaign.getPublishedRevision() == null) cleanup(oldUrl); }
        });
        return features.isControlledCampaignPublishing() ? repository.saveAndFlush(campaign) : repository.save(campaign);
    }

    @Transactional
    public HomepageCampaign removeMedia(Long id, boolean fallback, Long expectedVersion) {
        if (features.isControlledCampaignPublishing()) checkVersion(id, expectedVersion);
        return removeMedia(id, fallback);
    }

    @Transactional
    public HomepageCampaign uploadMobile(Long id, MultipartFile file, UUID requestId) {
        if (!features.isControlledCampaignPublishing()) throw new IllegalArgumentException("Controlled publishing is disabled.");
        var campaign = require(id);
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024)
            throw new IllegalArgumentException("Choose a mobile image of 5 MB or smaller.");
        String requestHash;
        try {requestHash = hash((file.getContentType() + ":" + hash(file.getBytes())).getBytes(StandardCharsets.UTF_8));}
        catch (IOException exception) {throw new IllegalStateException("Unable to read mobile image.", exception);}
        if (requestId != null) {
            var previous = repository.mobileRequestHash(id, requestId);
            if (previous.isPresent()) {
                if (!previous.get().equals(requestHash)) throw new IllegalArgumentException("This upload key was used for another image.");
                if (!requestId.equals(campaign.getMobileRequestId()))
                    throw new IllegalArgumentException("This mobile image has since been replaced. Reload the draft.");
                return campaign;
            }
        }
        String oldUrl = campaign.getMobileMediaUrl();
        var media = storage.uploadCampaignMedia(id, file, true);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) cleanup(media.url());
                // Keep previous versions available for publication rollback.
                else if (oldUrl != null && campaign.getPublishedRevision() == null) cleanup(oldUrl);
            }
        });
        campaign.setMobileMediaUrl(media.url()); campaign.setMobileRequestId(requestId);
        if (requestId != null) repository.recordMobileRequest(id, requestId, requestHash);
        return repository.saveAndFlush(campaign);
    }

    @Transactional
    public HomepageCampaign uploadMobile(Long id, MultipartFile file, UUID requestId, Long expectedVersion) {
        checkVersion(id, expectedVersion);
        return uploadMobile(id, file, requestId);
    }

    @Transactional
    public HomepageCampaign removeMobile(Long id) {
        if (!features.isControlledCampaignPublishing()) throw new IllegalArgumentException("Controlled publishing is disabled.");
        var campaign = require(id);
        String oldUrl = campaign.getMobileMediaUrl();
        campaign.setMobileMediaUrl(null); campaign.setMobileRequestId(null);
        if (campaign.getPublishedRevision() == null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup(oldUrl); }
            });
        }
        return repository.saveAndFlush(campaign);
    }

    @Transactional
    public HomepageCampaign removeMobile(Long id, Long expectedVersion) {
        checkVersion(id, expectedVersion);
        return removeMobile(id);
    }

    private void cleanup(String url) {
        if (url == null) return;
        try { storage.deleteCampaignMedia(url); }
        catch (RuntimeException exception) {
            log.error("Campaign media cleanup failed; object needs cleanup in R2: {}", url, exception);
        }
    }

    private HomepageCampaign require(Long id) {
        return repository.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Campaign not found."));
    }

    static boolean animated(HomepageCampaign campaign) {
        return "image/gif".equals(campaign.getMediaType())
                || (campaign.getMediaType() != null && campaign.getMediaType().startsWith("video/"));
    }

    private void validateActiveMedia(HomepageCampaign campaign) {
        if (!campaign.isActive()) return;
        if (campaign.getMediaUrl() == null) throw new IllegalArgumentException("Upload media before activating a campaign.");
        if (animated(campaign) && campaign.getFallbackMediaUrl() == null) {
            throw new IllegalArgumentException("Upload a static fallback image before activating animated media.");
        }

    }

    private static String hash(byte[] value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch (NoSuchAlgorithmException exception) {throw new IllegalStateException("SHA-256 unavailable.", exception);}
    }
}
