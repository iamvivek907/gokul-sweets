package com.gokulsweets.restaurant.campaign;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "campaign_publications")
@Getter @Setter
public class CampaignPublication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long campaignId;
    private String type;
    private String title;
    private String subtitle;
    private String altText;
    private Long branchId;
    private String mediaUrl;
    private String mediaType;
    private String mobileMediaUrl;
    private String fallbackMediaUrl;
    private String ctaLabel;
    private String ctaTarget;
    private Instant startAt;
    private Instant endAt;
    private int displayOrder;
    private Instant publishedAt;

    static CampaignPublication from(HomepageCampaign source, Instant now) {
        var copy = new CampaignPublication();
        copy.setCampaignId(source.getId());
        copy.setType(source.getType()); copy.setTitle(source.getTitle()); copy.setSubtitle(source.getSubtitle());
        copy.setAltText(source.getAltText()); copy.setBranchId(source.getBranchId());
        copy.setMediaUrl(source.getMediaUrl()); copy.setMediaType(source.getMediaType());
        copy.setMobileMediaUrl(source.getMobileMediaUrl()); copy.setFallbackMediaUrl(source.getFallbackMediaUrl());
        copy.setCtaLabel(source.getCtaLabel()); copy.setCtaTarget(source.getCtaTarget());
        copy.setStartAt(source.getStartAt()); copy.setEndAt(source.getEndAt());
        copy.setDisplayOrder(source.getDisplayOrder()); copy.setPublishedAt(now);
        return copy;
    }

    HomepageCampaign asCampaign() {
        var result = new HomepageCampaign();
        result.setId(campaignId); result.setType(type); result.setTitle(title); result.setSubtitle(subtitle);
        result.setAltText(altText); result.setBranchId(branchId);
        result.setMediaUrl(mediaUrl); result.setMediaType(mediaType);
        result.setMobileMediaUrl(mobileMediaUrl); result.setFallbackMediaUrl(fallbackMediaUrl);
        result.setCtaLabel(ctaLabel); result.setCtaTarget(ctaTarget);
        result.setStartAt(startAt); result.setEndAt(endAt); result.setDisplayOrder(displayOrder);
        result.setActive(true); result.setUpdatedAt(publishedAt);
        return result;
    }
}
