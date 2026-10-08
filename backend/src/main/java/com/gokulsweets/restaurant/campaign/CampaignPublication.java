package com.gokulsweets.restaurant.campaign;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Persisted campaign publication state. */
@Entity
@Table(name = "campaign_publications")
@Getter
@Setter
public class CampaignPublication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Column(name = "main_x")
    private int mainX = 50;

    @Column(name = "main_y")
    private int mainY = 50;

    @Column(name = "main_zoom")
    private int mainZoom = 100;

    @Column(name = "main_fit", length = 8)
    private String mainFit = "COVER";

    @Column(name = "mobile_x")
    private int mobileX = 50;

    @Column(name = "mobile_y")
    private int mobileY = 50;

    @Column(name = "mobile_zoom")
    private int mobileZoom = 100;

    @Column(name = "mobile_fit", length = 8)
    private String mobileFit = "COVER";

    private String fallbackMediaUrl;

    private String ctaLabel;

    private String ctaTarget;

    private Instant startAt;

    private Instant endAt;

    private int displayOrder;

    private Instant publishedAt;

    /**
     * Froms the operation.
     *
     * @param source the source
     * @param now the now
     * @return the from result
     */
    static CampaignPublication from(HomepageCampaign source, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignPublication.class, "from(HomepageCampaign,Instant)");
        try {
            var copy = new CampaignPublication();
            copy.setCampaignId(source.getId());
            copy.setType(source.getType());
            copy.setTitle(source.getTitle());
            copy.setSubtitle(source.getSubtitle());
            copy.setAltText(source.getAltText());
            copy.setBranchId(source.getBranchId());
            copy.setMediaUrl(source.getMediaUrl());
            copy.setMediaType(source.getMediaType());
            copy.setMobileMediaUrl(source.getMobileMediaUrl());
            copy.setMobileMediaType(source.getMobileMediaType());
            copy.setMainX(source.getMainX());
            copy.setMainY(source.getMainY());
            copy.setMainZoom(source.getMainZoom());
            copy.setMainFit(source.getMainFit());
            copy.setMobileX(source.getMobileX());
            copy.setMobileY(source.getMobileY());
            copy.setMobileZoom(source.getMobileZoom());
            copy.setMobileFit(source.getMobileFit());
            copy.setFallbackMediaUrl(source.getFallbackMediaUrl());
            copy.setCtaLabel(source.getCtaLabel());
            copy.setCtaTarget(source.getCtaTarget());
            copy.setStartAt(source.getStartAt());
            copy.setEndAt(source.getEndAt());
            copy.setDisplayOrder(source.getDisplayOrder());
            copy.setPublishedAt(now);
            return copy;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CampaignPublication.class,
                    "from(HomepageCampaign,Instant)");
        }
    }

    /**
     * Ases campaign.
     *
     * @return the as campaign result
     */
    HomepageCampaign asCampaign() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignPublication.class, "asCampaign()");
        try {
            var result = new HomepageCampaign();
            result.setId(campaignId);
            result.setType(type);
            result.setTitle(title);
            result.setSubtitle(subtitle);
            result.setAltText(altText);
            result.setBranchId(branchId);
            result.setMediaUrl(mediaUrl);
            result.setMediaType(mediaType);
            result.setMobileMediaUrl(mobileMediaUrl);
            result.setMobileMediaType(mobileMediaType);
            result.setMainX(mainX);
            result.setMainY(mainY);
            result.setMainZoom(mainZoom);
            result.setMainFit(mainFit);
            result.setMobileX(mobileX);
            result.setMobileY(mobileY);
            result.setMobileZoom(mobileZoom);
            result.setMobileFit(mobileFit);
            result.setFallbackMediaUrl(fallbackMediaUrl);
            result.setCtaLabel(ctaLabel);
            result.setCtaTarget(ctaTarget);
            result.setStartAt(startAt);
            result.setEndAt(endAt);
            result.setDisplayOrder(displayOrder);
            result.setActive(true);
            result.setUpdatedAt(publishedAt);
            return result;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CampaignPublication.class, "asCampaign()");
        }
    }
}
