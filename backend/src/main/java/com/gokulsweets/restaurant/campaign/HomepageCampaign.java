package com.gokulsweets.restaurant.campaign;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "homepage_campaigns")
@Getter
@Setter
public class HomepageCampaign {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    @JsonIgnore @Column(length = 64) private String creationRequestHash;
    @JsonIgnore private UUID mediaRequestId;
    @JsonIgnore private UUID fallbackRequestId;

    @PrePersist
    void create() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate
    void update() { updatedAt = Instant.now(); }

    public boolean visibleAt(Instant now) {
        // Inclusive start / exclusive end avoids one expired campaign masking the next scheduled campaign.
        return active && mediaUrl != null && (startAt == null || !now.isBefore(startAt))
                && (endAt == null || now.isBefore(endAt));
    }
}
