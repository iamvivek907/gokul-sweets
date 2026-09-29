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
    private String mobileMediaType;
    private int mainX = 50;
    private int mainY = 50;
    private int mainZoom = 100;
    @Column(length = 8) private String mainFit = "COVER";
    private int mobileX = 50;
    private int mobileY = 50;
    private int mobileZoom = 100;
    @Column(length = 8) private String mobileFit = "COVER";
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
        copy.setMobileMediaUrl(source.getMobileMediaUrl()); copy.setMobileMediaType(source.getMobileMediaType());
        copy.setMainX(source.getMainX()); copy.setMainY(source.getMainY()); copy.setMainZoom(source.getMainZoom()); copy.setMainFit(source.getMainFit());
        copy.setMobileX(source.getMobileX()); copy.setMobileY(source.getMobileY()); copy.setMobileZoom(source.getMobileZoom()); copy.setMobileFit(source.getMobileFit());
        copy.setFallbackMediaUrl(source.getFallbackMediaUrl());
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
        result.setMobileMediaUrl(mobileMediaUrl); result.setMobileMediaType(mobileMediaType);
        result.setMainX(mainX); result.setMainY(mainY); result.setMainZoom(mainZoom); result.setMainFit(mainFit);
        result.setMobileX(mobileX); result.setMobileY(mobileY); result.setMobileZoom(mobileZoom); result.setMobileFit(mobileFit);
        result.setFallbackMediaUrl(fallbackMediaUrl);
        result.setCtaLabel(ctaLabel); result.setCtaTarget(ctaTarget);
        result.setStartAt(startAt); result.setEndAt(endAt); result.setDisplayOrder(displayOrder);
        result.setActive(true); result.setUpdatedAt(publishedAt);
        return result;
    }
}
