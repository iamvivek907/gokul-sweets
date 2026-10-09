package com.gokulsweets.restaurant.campaign;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.constraints.*;

import java.time.Instant;

/**
 * Immutable campaign request data contract.
 *
 * @param type the type
 * @param title the title
 * @param subtitle the subtitle
 * @param ctaLabel the cta label
 * @param ctaTarget the cta target
 * @param startAt the start at
 * @param endAt the end at
 * @param active the active
 * @param displayOrder the display order
 * @param altText the alt text
 * @param branchId the branch id
 * @param mainX the main x
 * @param mainY the main y
 * @param mainZoom the main zoom
 * @param mainFit the main fit
 * @param mobileX the mobile x
 * @param mobileY the mobile y
 * @param mobileZoom the mobile zoom
 * @param mobileFit the mobile fit
 */
public record CampaignRequest(
        @NotNull @Pattern(regexp = "HERO|FEATURE") String type,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 240) String subtitle,
        @Size(max = 60) String ctaLabel,
        @Pattern(regexp = "^/(menu|cart|about)$") String ctaTarget,
        Instant startAt,
        Instant endAt,
        boolean active,
        @Min(0) @Max(10000) int displayOrder,
        @Size(max = 180) String altText,
        @Positive Long branchId,
        @Min(0) @Max(100) Integer mainX,
        @Min(0) @Max(100) Integer mainY,
        @Min(100) @Max(300) Integer mainZoom,
        @Pattern(regexp = "COVER|CONTAIN") String mainFit,
        @Min(0) @Max(100) Integer mobileX,
        @Min(0) @Max(100) Integer mobileY,
        @Min(100) @Max(300) Integer mobileZoom,
        @Pattern(regexp = "COVER|CONTAIN") String mobileFit) {

    /**
     * Creates a campaign request instance.
     *
     * @param type the type
     * @param title the title
     * @param subtitle the subtitle
     * @param ctaLabel the cta label
     * @param ctaTarget the cta target
     * @param startAt the start at
     * @param endAt the end at
     * @param active the active
     * @param displayOrder the display order
     * @param altText the alt text
     * @param branchId the branch id
     */
    public CampaignRequest(
            String type,
            String title,
            String subtitle,
            String ctaLabel,
            String ctaTarget,
            Instant startAt,
            Instant endAt,
            boolean active,
            int displayOrder,
            String altText,
            Long branchId) {
        this(
                type,
                title,
                subtitle,
                ctaLabel,
                ctaTarget,
                startAt,
                endAt,
                active,
                displayOrder,
                altText,
                branchId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * Creates a campaign request instance.
     *
     * @param type the type
     * @param title the title
     * @param subtitle the subtitle
     * @param ctaLabel the cta label
     * @param ctaTarget the cta target
     * @param startAt the start at
     * @param endAt the end at
     * @param active the active
     * @param displayOrder the display order
     */
    public CampaignRequest(
            String type,
            String title,
            String subtitle,
            String ctaLabel,
            String ctaTarget,
            Instant startAt,
            Instant endAt,
            boolean active,
            int displayOrder) {
        this(
                type,
                title,
                subtitle,
                ctaLabel,
                ctaTarget,
                startAt,
                endAt,
                active,
                displayOrder,
                null,
                null);
    }

    /**
     * Validates campaign data.
     *
     * @throws IllegalArgumentException when the method rejects the request with {@code Campaign end
     *     must be after its start.}; {@code Provide both a button label and a destination, or
     *     neither.}
     */
    public void validate() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CampaignRequest.class, "validate()");
        try {
            if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
                throw new IllegalArgumentException("Campaign end must be after its start.");
            }
            if ((ctaLabel != null && !ctaLabel.isBlank()) != (ctaTarget != null)) {
                throw new IllegalArgumentException(
                        "Provide both a button label and a destination, or neither.");
            }
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CampaignRequest.class, "validate()");
        }
    }
}
