package com.gokulsweets.restaurant.campaign;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.storage.R2StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Coordinates campaign operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final HomepageCampaignRepository repository;

    private final R2StorageService storage;

    private final Clock inventoryClock;

    private final EnhancementProperties features;

    private final CampaignPublicationRepository publications;

    /**
     * Creates a campaign draft; when an idempotency key is supplied, reuses the matching draft and
     * rejects reuse with different request fields.
     *
     * <p>Delegates to {@code repository.createDraft(...)}, {@code
     * repository.findByCreationRequestId(...)}.
     *
     * @param request the request supplied to this method
     * @param requestId the request id supplied to this method
     * @return the {@code HomepageCampaign} result
     * @throws IllegalArgumentException when the method rejects the request with {@code This draft
     *     request was already used with different fields. Reopen the saved draft before editing.}
     */
    @Transactional
    public HomepageCampaign create(CampaignRequest request, UUID requestId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "create(CampaignRequest,UUID)");
        try {
            if (requestId == null) return save(null, request);
            request.validate();
            var canonical = new StringBuilder();
            for (Object field :
                    new Object[] {
                        request.type(),
                        request.title(),
                        request.subtitle(),
                        request.ctaLabel(),
                        request.ctaTarget(),
                        request.startAt(),
                        request.endAt(),
                        request.active(),
                        request.displayOrder(),
                        request.altText(),
                        request.branchId(),
                        request.mainX(),
                        request.mainY(),
                        request.mainZoom(),
                        request.mainFit(),
                        request.mobileX(),
                        request.mobileY(),
                        request.mobileZoom(),
                        request.mobileFit()
                    }) {
                String value = field == null ? null : field.toString();
                canonical
                        .append(value == null ? -1 : value.length())
                        .append(':')
                        .append(value == null ? "" : value);
            }
            String hash = hash(canonical.toString().getBytes(StandardCharsets.UTF_8));
            int inserted =
                    repository.createDraft(requestId, hash, request.title().trim(), request.type());
            var campaign = repository.findByCreationRequestId(requestId).orElseThrow();
            if (!hash.equals(campaign.getCreationRequestHash()))
                throw new IllegalArgumentException(
                        "This draft request was already used with different fields. Reopen the"
                                + " saved draft before editing.");
            return inserted == 0 ? campaign : save(campaign.getId(), request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignService.class,
                    "create(CampaignRequest,UUID)");
        }
    }

    /**
     * Returns currently visible campaigns, using published revisions when controlled publishing is
     * enabled.
     *
     * <p>Delegates to {@code repository.findAllByOrderByDisplayOrderAscIdAsc(...)}, {@code
     * repository.findByActiveTrueOrderByDisplayOrderAscIdAsc(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @return the {@code List<HomepageCampaign>} result
     */
    @Transactional(readOnly = true)
    public List<HomepageCampaign> active(Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "active(Long)");
        try {
            if (features.isControlledCampaignPublishing()) {
                return repository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                        .filter(c -> c.getPublishedRevision() != null)
                        .map(c -> publications.findById(c.getPublishedRevision()).orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .map(CampaignPublication::asCampaign)
                        .filter(
                                c ->
                                        (c.getBranchId() == null
                                                        || c.getBranchId().equals(branchId))
                                                && c.visibleAt(inventoryClock.instant()))
                        .sorted(
                                java.util.Comparator.comparingInt(HomepageCampaign::getDisplayOrder)
                                        .thenComparing(HomepageCampaign::getId))
                        .toList();
            }
            return repository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                    .filter(campaign -> campaign.visibleAt(inventoryClock.instant()))
                    .toList();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CampaignService.class, "active(Long)");
        }
    }

    /**
     * Returns currently visible campaigns, using published revisions when controlled publishing is
     * enabled.
     *
     * @return the value of {@code active(null)}
     */
    public List<HomepageCampaign> active() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "active()");
        try {
            return active(null);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CampaignService.class, "active()");
        }
    }

    /**
     * Validates and persists campaign fields, checking expected revisions when applicable and
     * publishing a snapshot when controlled activation requires one.
     *
     * <p>Delegates to {@code repository.saveAndFlush(...)}, {@code repository.save(...)}.
     *
     * @param id the id supplied to this method
     * @param request the request supplied to this method
     * @return the {@code HomepageCampaign} result
     * @throws IllegalArgumentException when the method rejects the request with {@code Add image
     *     description before publishing.}
     */
    @Transactional
    public HomepageCampaign save(Long id, CampaignRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "save(Long,CampaignRequest)");
        try {
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
            if (request.mainX() != null) campaign.setMainX(request.mainX());
            if (request.mainY() != null) campaign.setMainY(request.mainY());
            if (request.mainZoom() != null) campaign.setMainZoom(request.mainZoom());
            if (request.mainFit() != null) campaign.setMainFit(request.mainFit());
            if (request.mobileX() != null) campaign.setMobileX(request.mobileX());
            if (request.mobileY() != null) campaign.setMobileY(request.mobileY());
            if (request.mobileZoom() != null) campaign.setMobileZoom(request.mobileZoom());
            if (request.mobileFit() != null) campaign.setMobileFit(request.mobileFit());
            if (features.isControlledCampaignPublishing()) {
                campaign.setAltText(request.altText() == null ? null : request.altText().trim());
                campaign.setBranchId(request.branchId());
            }
            validateActiveMedia(campaign);
            if (features.isControlledCampaignPublishing() && request.active()) {
                if (campaign.getAltText() == null || campaign.getAltText().isBlank())
                    throw new IllegalArgumentException("Add image description before publishing.");
                campaign = repository.saveAndFlush(campaign);
                var publication =
                        publications.saveAndFlush(
                                CampaignPublication.from(campaign, inventoryClock.instant()));
                campaign.setPublishedRevision(publication.getId());
                return repository.saveAndFlush(campaign);
            }
            return features.isControlledCampaignPublishing()
                    ? repository.saveAndFlush(campaign)
                    : repository.save(campaign);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CampaignService.class, "save(Long,CampaignRequest)");
        }
    }

    /**
     * Validates and persists campaign fields, checking expected revisions when applicable and
     * publishing a snapshot when controlled activation requires one.
     *
     * @param id the id supplied to this method
     * @param request the request supplied to this method
     * @param expectedVersion the expected version supplied to this method
     * @return the value of {@code save(id, request)}
     */
    @Transactional
    public HomepageCampaign save(Long id, CampaignRequest request, Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "save(Long,CampaignRequest,Long)");
        try {
            if (features.isControlledCampaignPublishing()) checkVersion(id, expectedVersion);
            return save(id, request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignService.class,
                    "save(Long,CampaignRequest,Long)");
        }
    }

    /**
     * Checks version.
     *
     * @param id the id
     * @param expected the expected
     */
    private void checkVersion(Long id, Long expected) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "checkVersion(Long,Long)");
        try {
            var current = require(id);
            if (expected == null || expected != current.getEditVersion()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Campaign changed in another editor. Reload the draft before saving.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CampaignService.class, "checkVersion(Long,Long)");
        }
    }

    /**
     * Returns a campaign's publication revisions newest first, rejecting disabled controlled
     * publishing or an unknown campaign.
     *
     * <p>Delegates to {@code repository.existsById(...)}.
     *
     * @param id the id supplied to this method
     * @return the value of {@code publications.findByCampaignIdOrderByIdDesc(id)}
     * @throws IllegalArgumentException when the method rejects the request with {@code Campaign not
     *     found.}; {@code Controlled publishing is disabled.}
     */
    @Transactional(readOnly = true)
    public List<CampaignPublication> history(Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "history(Long)");
        try {
            if (!features.isControlledCampaignPublishing())
                throw new IllegalArgumentException("Controlled publishing is disabled.");
            if (!repository.existsById(id))
                throw new IllegalArgumentException("Campaign not found.");
            return publications.findByCampaignIdOrderByIdDesc(id);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CampaignService.class, "history(Long)");
        }
    }

    /**
     * Restores the campaign's published-revision pointer to a revision belonging to that campaign,
     * checking an expected version when supplied.
     *
     * <p>Delegates to {@code repository.saveAndFlush(...)}.
     *
     * @param id the id supplied to this method
     * @param revision the revision supplied to this method
     * @return the value of {@code repository.saveAndFlush(campaign)}
     * @throws IllegalArgumentException when the method rejects the request with {@code Controlled
     *     publishing is disabled.}; {@code Revision belongs to another campaign.}
     */
    @Transactional
    public HomepageCampaign rollback(Long id, Long revision) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "rollback(Long,Long)");
        try {
            if (!features.isControlledCampaignPublishing())
                throw new IllegalArgumentException("Controlled publishing is disabled.");
            var campaign = require(id);
            var previous =
                    publications
                            .findById(revision)
                            .orElseThrow(() -> new IllegalArgumentException("Revision not found."));
            if (!previous.getCampaignId().equals(id))
                throw new IllegalArgumentException("Revision belongs to another campaign.");
            campaign.setPublishedRevision(revision);
            return repository.saveAndFlush(campaign);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CampaignService.class, "rollback(Long,Long)");
        }
    }

    /**
     * Restores the campaign's published-revision pointer to a revision belonging to that campaign,
     * checking an expected version when supplied.
     *
     * @param id the id supplied to this method
     * @param revision the revision supplied to this method
     * @param expectedVersion the expected version supplied to this method
     * @return the value of {@code rollback(id, revision)}
     */
    @Transactional
    public HomepageCampaign rollback(Long id, Long revision, Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "rollback(Long,Long,Long)");
        try {
            if (features.isControlledCampaignPublishing()) checkVersion(id, expectedVersion);
            return rollback(id, revision);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CampaignService.class, "rollback(Long,Long,Long)");
        }
    }

    /**
     * Stores campaign media and updates the draft, preserving idempotency checks and coordinating
     * object cleanup with the transaction outcome.
     *
     * @param id the id supplied to this method
     * @param file the file supplied to this method
     * @param fallback the fallback supplied to this method
     * @return the value of {@code upload(id, file, fallback, null)}
     */
    @Transactional
    public HomepageCampaign upload(Long id, MultipartFile file, boolean fallback) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "upload(Long,MultipartFile,boolean)");
        try {
            return upload(id, file, fallback, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignService.class,
                    "upload(Long,MultipartFile,boolean)");
        }
    }

    /**
     * Stores campaign media and updates the draft, preserving idempotency checks and coordinating
     * object cleanup with the transaction outcome.
     *
     * <p>Delegates to {@code repository.mediaRequestHash(...)}, {@code
     * storage.uploadCampaignMedia(...)}, {@code repository.recordMediaRequest(...)}, {@code
     * repository.saveAndFlush(...)}.
     *
     * @param id the id supplied to this method
     * @param file the file supplied to this method
     * @param fallback the fallback supplied to this method
     * @param requestId the request id supplied to this method
     * @return the {@code HomepageCampaign} result
     * @throws IllegalArgumentException when the method rejects the request with {@code Choose an
     *     image up to 5 MB or video up to 50 MB.}; {@code This upload has since been replaced or
     *     removed. Reload the campaign before changing media.}; {@code This upload request was
     *     already used for a different file.}
     * @throws IllegalStateException when the method rejects the request with {@code Unable to read
     *     campaign media.}
     */
    @Transactional
    public HomepageCampaign upload(Long id, MultipartFile file, boolean fallback, UUID requestId) {
        final long __gokulMethodStartedNanos_ =
                MethodTiming.start(
                        CampaignService.class, "upload(Long,MultipartFile,boolean,UUID)");
        try {
            HomepageCampaign campaign = require(id);
            String requestHash = null;
            if (requestId != null) {
                boolean video =
                        file != null
                                && !fallback
                                && ("video/mp4".equals(file.getContentType())
                                        || "video/webm".equals(file.getContentType()));
                long limit = video ? 50L * 1024 * 1024 : 5L * 1024 * 1024;
                if (file == null || file.isEmpty() || file.getSize() > limit)
                    throw new IllegalArgumentException(
                            "Choose an image up to 5 MB or video up to 50 MB.");
                try {
                    requestHash =
                            hash(
                                    (file.getContentType() + ":" + hash(file.getBytes()))
                                            .getBytes(StandardCharsets.UTF_8));
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to read campaign media.", exception);
                }
                var previousHash = repository.mediaRequestHash(id, fallback, requestId);
                if (previousHash.isPresent()) {
                    if (!previousHash.get().equals(requestHash))
                        throw new IllegalArgumentException(
                                "This upload request was already used for a different file.");
                    if (!requestId.equals(
                            fallback
                                    ? campaign.getFallbackRequestId()
                                    : campaign.getMediaRequestId()))
                        throw new IllegalArgumentException(
                                "This upload has since been replaced or removed. Reload the"
                                        + " campaign before changing media.");
                    return campaign;
                }
            }
            String oldUrl = fallback ? campaign.getFallbackMediaUrl() : campaign.getMediaUrl();
            var media = storage.uploadCampaignMedia(id, file, fallback);
            // The database and object storage do not share a transaction: keep old media until
            // commit.
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {

                        /**
                         * Afters completion.
                         *
                         * @param status the status
                         */
                        @Override
                        public void afterCompletion(int status) {
                            final long __gokulMethodStartedNanos =
                                    MethodTiming.start(
                                            CampaignService.class,
                                            "upload(Long,MultipartFile,boolean,UUID)/anonymous[1]/afterCompletion(int)");
                            try {
                                if (status != STATUS_COMMITTED) cleanup(media.url());
                                else if (!features.isControlledCampaignPublishing()
                                        && campaign.getPublishedRevision() == null) cleanup(oldUrl);
                            } finally {
                                MethodTiming.finish(
                                        __gokulMethodStartedNanos,
                                        CampaignService.class,
                                        "upload(Long,MultipartFile,boolean,UUID)/anonymous[1]/afterCompletion(int)");
                            }
                        }
                    });
            if (fallback) {
                campaign.setFallbackMediaUrl(media.url());
                campaign.setFallbackRequestId(requestId);
            } else {
                campaign.setMediaUrl(media.url());
                campaign.setMediaType(media.contentType());
                campaign.setMediaRequestId(requestId);
            }
            validateActiveMedia(campaign);
            if (requestId != null)
                repository.recordMediaRequest(id, fallback, requestId, requestHash);
            return features.isControlledCampaignPublishing()
                    ? repository.saveAndFlush(campaign)
                    : repository.save(campaign);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos_,
                    CampaignService.class,
                    "upload(Long,MultipartFile,boolean,UUID)");
        }
    }

    /**
     * Stores campaign media and updates the draft, preserving idempotency checks and coordinating
     * object cleanup with the transaction outcome.
     *
     * @param id the id supplied to this method
     * @param file the file supplied to this method
     * @param fallback the fallback supplied to this method
     * @param requestId the request id supplied to this method
     * @param expectedVersion the expected version supplied to this method
     * @return the value of {@code upload(id, file, fallback, requestId)}
     */
    @Transactional
    public HomepageCampaign upload(
            Long id, MultipartFile file, boolean fallback, UUID requestId, Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CampaignService.class, "upload(Long,MultipartFile,boolean,UUID,Long)");
        try {
            if (features.isControlledCampaignPublishing()) checkVersion(id, expectedVersion);
            return upload(id, file, fallback, requestId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignService.class,
                    "upload(Long,MultipartFile,boolean,UUID,Long)");
        }
    }

    /**
     * Removes media.
     *
     * @param id the id
     * @param fallback the fallback
     * @return the remove media result
     */
    @Transactional
    public HomepageCampaign removeMedia(Long id, boolean fallback) {
        final long __gokulMethodStartedNanos_ =
                MethodTiming.start(CampaignService.class, "removeMedia(Long,boolean)");
        try {
            HomepageCampaign campaign = require(id);
            String oldUrl = fallback ? campaign.getFallbackMediaUrl() : campaign.getMediaUrl();
            if (fallback) {
                campaign.setFallbackMediaUrl(null);
                campaign.setFallbackRequestId(null);
                if (animated(campaign)) campaign.setActive(false);
            } else {
                campaign.setMediaUrl(null);
                campaign.setMediaType(null);
                campaign.setActive(false);
                campaign.setMediaRequestId(null);
            }
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {

                        /** Afters commit. */
                        @Override
                        public void afterCommit() {
                            final long __gokulMethodStartedNanos =
                                    MethodTiming.start(
                                            CampaignService.class,
                                            "removeMedia(Long,boolean)/anonymous[2]/afterCommit()");
                            try {
                                if (!features.isControlledCampaignPublishing()
                                        && campaign.getPublishedRevision() == null) cleanup(oldUrl);
                            } finally {
                                MethodTiming.finish(
                                        __gokulMethodStartedNanos,
                                        CampaignService.class,
                                        "removeMedia(Long,boolean)/anonymous[2]/afterCommit()");
                            }
                        }
                    });
            return features.isControlledCampaignPublishing()
                    ? repository.saveAndFlush(campaign)
                    : repository.save(campaign);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos_, CampaignService.class, "removeMedia(Long,boolean)");
        }
    }

    /**
     * Removes media.
     *
     * @param id the id
     * @param fallback the fallback
     * @param expectedVersion the expected version
     * @return the remove media result
     */
    @Transactional
    public HomepageCampaign removeMedia(Long id, boolean fallback, Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "removeMedia(Long,boolean,Long)");
        try {
            if (features.isControlledCampaignPublishing()) checkVersion(id, expectedVersion);
            return removeMedia(id, fallback);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignService.class,
                    "removeMedia(Long,boolean,Long)");
        }
    }

    /**
     * Uploads mobile.
     *
     * @param id the id
     * @param file the file
     * @param requestId the request id
     * @return the upload mobile result
     */
    @Transactional
    public HomepageCampaign uploadMobile(Long id, MultipartFile file, UUID requestId) {
        final long __gokulMethodStartedNanos_ =
                MethodTiming.start(CampaignService.class, "uploadMobile(Long,MultipartFile,UUID)");
        try {
            if (!features.isControlledCampaignPublishing())
                throw new IllegalArgumentException("Controlled publishing is disabled.");
            var campaign = require(id);
            String mobileType = file == null ? "" : file.getContentType();
            long limit =
                    mobileType != null && mobileType.startsWith("video/")
                            ? 50L * 1024 * 1024
                            : 5L * 1024 * 1024;
            if (file == null || file.isEmpty() || file.getSize() > limit)
                throw new IllegalArgumentException(
                        "Choose a mobile image up to 5 MB or video up to 50 MB.");
            String requestHash;
            try {
                requestHash =
                        hash(
                                (file.getContentType() + ":" + hash(file.getBytes()))
                                        .getBytes(StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read mobile media.", exception);
            }
            if (requestId != null) {
                var previous = repository.mobileRequestHash(id, requestId);
                if (previous.isPresent()) {
                    if (!previous.get().equals(requestHash))
                        throw new IllegalArgumentException(
                                "This upload key was used for another file.");
                    if (!requestId.equals(campaign.getMobileRequestId()))
                        throw new IllegalArgumentException(
                                "This mobile media has since been replaced. Reload the draft.");
                    return campaign;
                }
            }
            String oldUrl = campaign.getMobileMediaUrl();
            var media = storage.uploadMobileCampaignMedia(id, file);
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {

                        /**
                         * Afters completion.
                         *
                         * @param status the status
                         */
                        @Override
                        public void afterCompletion(int status) {
                            final long __gokulMethodStartedNanos =
                                    MethodTiming.start(
                                            CampaignService.class,
                                            "uploadMobile(Long,MultipartFile,UUID)/anonymous[3]/afterCompletion(int)");
                            try {
                                if (status != STATUS_COMMITTED) cleanup(media.url());
                                else // Keep previous versions available for publication rollback.
                                if (oldUrl != null && campaign.getPublishedRevision() == null)
                                    cleanup(oldUrl);
                            } finally {
                                MethodTiming.finish(
                                        __gokulMethodStartedNanos,
                                        CampaignService.class,
                                        "uploadMobile(Long,MultipartFile,UUID)/anonymous[3]/afterCompletion(int)");
                            }
                        }
                    });
            campaign.setMobileMediaUrl(media.url());
            campaign.setMobileMediaType(media.contentType());
            campaign.setMobileRequestId(requestId);
            if (requestId != null) repository.recordMobileRequest(id, requestId, requestHash);
            return repository.saveAndFlush(campaign);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos_,
                    CampaignService.class,
                    "uploadMobile(Long,MultipartFile,UUID)");
        }
    }

    /**
     * Uploads mobile.
     *
     * @param id the id
     * @param file the file
     * @param requestId the request id
     * @param expectedVersion the expected version
     * @return the upload mobile result
     */
    @Transactional
    public HomepageCampaign uploadMobile(
            Long id, MultipartFile file, UUID requestId, Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CampaignService.class, "uploadMobile(Long,MultipartFile,UUID,Long)");
        try {
            checkVersion(id, expectedVersion);
            return uploadMobile(id, file, requestId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignService.class,
                    "uploadMobile(Long,MultipartFile,UUID,Long)");
        }
    }

    /**
     * Removes mobile.
     *
     * @param id the id
     * @return the remove mobile result
     */
    @Transactional
    public HomepageCampaign removeMobile(Long id) {
        final long __gokulMethodStartedNanos_ =
                MethodTiming.start(CampaignService.class, "removeMobile(Long)");
        try {
            if (!features.isControlledCampaignPublishing())
                throw new IllegalArgumentException("Controlled publishing is disabled.");
            var campaign = require(id);
            String oldUrl = campaign.getMobileMediaUrl();
            campaign.setMobileMediaUrl(null);
            campaign.setMobileMediaType(null);
            campaign.setMobileRequestId(null);
            if (campaign.getPublishedRevision() == null) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {

                            /** Afters commit. */
                            @Override
                            public void afterCommit() {
                                final long __gokulMethodStartedNanos =
                                        MethodTiming.start(
                                                CampaignService.class,
                                                "removeMobile(Long)/anonymous[4]/afterCommit()");
                                try {
                                    cleanup(oldUrl);
                                } finally {
                                    MethodTiming.finish(
                                            __gokulMethodStartedNanos,
                                            CampaignService.class,
                                            "removeMobile(Long)/anonymous[4]/afterCommit()");
                                }
                            }
                        });
            }
            return repository.saveAndFlush(campaign);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos_, CampaignService.class, "removeMobile(Long)");
        }
    }

    /**
     * Removes mobile.
     *
     * @param id the id
     * @param expectedVersion the expected version
     * @return the remove mobile result
     */
    @Transactional
    public HomepageCampaign removeMobile(Long id, Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "removeMobile(Long,Long)");
        try {
            checkVersion(id, expectedVersion);
            return removeMobile(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CampaignService.class, "removeMobile(Long,Long)");
        }
    }

    /**
     * Attempts to delete campaign media from storage; logs deletion failures for later cleanup
     * rather than propagating them.
     *
     * <p>Delegates to {@code storage.deleteCampaignMedia(...)}.
     *
     * @param url the url supplied to this method
     */
    private void cleanup(String url) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "cleanup(String)");
        try {
            if (url == null) return;
            try {
                storage.deleteCampaignMedia(url);
            } catch (RuntimeException exception) {
                log.error(
                        "Campaign media cleanup failed; object needs cleanup in R2: {}",
                        url,
                        exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CampaignService.class, "cleanup(String)");
        }
    }

    /**
     * Loads the campaign under the repository's update lock or rejects an unknown identifier.
     *
     * <p>Delegates to {@code repository.findForUpdate(...)}.
     *
     * @param id the id supplied to this method
     * @return the {@code HomepageCampaign} result
     */
    private HomepageCampaign require(Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "require(Long)");
        try {
            return repository
                    .findForUpdate(id)
                    .orElseThrow(() -> new IllegalArgumentException("Campaign not found."));
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CampaignService.class, "require(Long)");
        }
    }

    /**
     * Returns whether campaign media is a GIF or has a video content type.
     *
     * @param campaign the campaign supplied to this method
     * @return the {@code boolean} result
     */
    static boolean animated(HomepageCampaign campaign) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "animated(HomepageCampaign)");
        try {
            return "image/gif".equals(campaign.getMediaType())
                    || (campaign.getMediaType() != null
                            && campaign.getMediaType().startsWith("video/"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CampaignService.class, "animated(HomepageCampaign)");
        }
    }

    /**
     * Validates active media.
     *
     * @param campaign the campaign
     */
    private void validateActiveMedia(HomepageCampaign campaign) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "validateActiveMedia(HomepageCampaign)");
        try {
            if (!campaign.isActive()) return;
            if (campaign.getMediaUrl() == null)
                throw new IllegalArgumentException("Upload media before activating a campaign.");
            if (animated(campaign) && campaign.getFallbackMediaUrl() == null) {
                throw new IllegalArgumentException(
                        "Upload a static fallback image before activating animated media.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignService.class,
                    "validateActiveMedia(HomepageCampaign)");
        }
    }

    /**
     * Returns the SHA-256 digest of the supplied value as hexadecimal text.
     *
     * @param value the value supplied to this method
     * @return the {@code String} result
     * @throws IllegalStateException when the method rejects the request with {@code SHA-256
     *     unavailable.}
     */
    private static String hash(byte[] value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignService.class, "hash(byte[])");
        try {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 unavailable.", exception);
            }
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CampaignService.class, "hash(byte[])");
        }
    }
}
