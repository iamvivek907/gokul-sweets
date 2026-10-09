package com.gokulsweets.restaurant.campaign;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Persisted homepage campaign state. */
@Entity
@Table(name = "homepage_campaigns")
@Getter
@Setter
public class HomepageCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String type = "HERO";

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 240)
    private String subtitle;

    @Column(length = 1000)
    private String mediaUrl;

    @Column(length = 40)
    private String mediaType;

    @Column(length = 1000)
    private String fallbackMediaUrl;

    @Column(length = 180)
    private String altText;

    private Long branchId;

    @Column(length = 1000)
    private String mobileMediaUrl;

    @Column(length = 40)
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

    @JsonIgnore private UUID mobileRequestId;

    private Long publishedRevision;

    @Version private long editVersion;

    @Column(length = 60)
    private String ctaLabel;

    @Column(length = 240)
    private String ctaTarget;

    private Instant startAt;

    private Instant endAt;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @JsonIgnore private UUID creationRequestId;

    @JsonIgnore
    @Column(length = 64)
    private String creationRequestHash;

    @JsonIgnore private UUID mediaRequestId;

    @JsonIgnore private UUID fallbackRequestId;

    /**
     * Initializes creation and update timestamps together before the campaign is first persisted.
     */
    @PrePersist
    void create() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(HomepageCampaign.class, "create()");
        try {
            createdAt = Instant.now();
            updatedAt = createdAt;
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, HomepageCampaign.class, "create()");
        }
    }

    /** Refreshes the campaign update timestamp before an existing row is updated. */
    @PreUpdate
    void update() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(HomepageCampaign.class, "update()");
        try {
            updatedAt = Instant.now();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, HomepageCampaign.class, "update()");
        }
    }

    /**
     * Visibles at.
     *
     * @param now the now
     * @return the visible at result
     */
    public boolean visibleAt(Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(HomepageCampaign.class, "visibleAt(Instant)");
        try {
            // Inclusive start / exclusive end avoids one expired campaign masking the next
            // scheduled campaign.
            return active
                    && mediaUrl != null
                    && (startAt == null || !now.isBefore(startAt))
                    && (endAt == null || now.isBefore(endAt));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, HomepageCampaign.class, "visibleAt(Instant)");
        }
    }
}
