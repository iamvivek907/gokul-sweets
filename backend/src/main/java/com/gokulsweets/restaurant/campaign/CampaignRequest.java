package com.gokulsweets.restaurant.campaign;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CampaignRequest(
        @NotNull @Pattern(regexp = "HERO|FEATURE") String type,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 240) String subtitle,
        @Size(max = 60) String ctaLabel,
        @Pattern(regexp = "^/(menu|cart|about)$") String ctaTarget,
        Instant startAt, Instant endAt,
        boolean active, @Min(0) @Max(10000) int displayOrder,
        @Size(max = 180) String altText, @Positive Long branchId
) {
    public CampaignRequest(String type, String title, String subtitle, String ctaLabel, String ctaTarget,
                           Instant startAt, Instant endAt, boolean active, int displayOrder) {
        this(type, title, subtitle, ctaLabel, ctaTarget, startAt, endAt, active, displayOrder, null, null);
    }
    public void validate() {
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Campaign end must be after its start.");
        }
        if ((ctaLabel != null && !ctaLabel.isBlank()) != (ctaTarget != null)) {
            throw new IllegalArgumentException("Provide both a button label and a destination, or neither.");
        }
    }
}
