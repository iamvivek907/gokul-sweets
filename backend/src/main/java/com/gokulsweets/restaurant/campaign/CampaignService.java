package com.gokulsweets.restaurant.campaign;

import com.gokulsweets.restaurant.storage.R2StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {
    private final HomepageCampaignRepository repository;
    private final R2StorageService storage;
    private final Clock inventoryClock;

    @Transactional(readOnly = true)
    public List<HomepageCampaign> active() {
        return repository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .filter(campaign -> campaign.visibleAt(inventoryClock.instant())).toList();
    }

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
        validateActiveMedia(campaign);
        return repository.save(campaign);
    }

    @Transactional
    public HomepageCampaign upload(Long id, MultipartFile file, boolean fallback) {
        HomepageCampaign campaign = require(id);
        String oldUrl = fallback ? campaign.getFallbackMediaUrl() : campaign.getMediaUrl();
        var media = storage.uploadCampaignMedia(id, file, fallback);
        // The database and object storage do not share a transaction: keep old media until commit.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                cleanup(status == STATUS_COMMITTED ? oldUrl : media.url());
            }
        });
        if (fallback) campaign.setFallbackMediaUrl(media.url());
        else {
            campaign.setMediaUrl(media.url());
            campaign.setMediaType(media.contentType());
        }
        validateActiveMedia(campaign);
        return repository.save(campaign);
    }

    @Transactional
    public HomepageCampaign removeMedia(Long id, boolean fallback) {
        HomepageCampaign campaign = require(id);
        String oldUrl = fallback ? campaign.getFallbackMediaUrl() : campaign.getMediaUrl();
        if (fallback) {
            campaign.setFallbackMediaUrl(null);
            if (animated(campaign)) campaign.setActive(false);
        } else {
            campaign.setMediaUrl(null); campaign.setMediaType(null); campaign.setActive(false);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cleanup(oldUrl); }
        });
        return repository.save(campaign);
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
}
