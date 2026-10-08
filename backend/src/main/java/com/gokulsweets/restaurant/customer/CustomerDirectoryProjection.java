package com.gokulsweets.restaurant.customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Backend customer directory projection contract and implementation. */
public interface CustomerDirectoryProjection {

    /**
     * Returns id.
     *
     * @return the get id result
     */
    Long getId();

    /**
     * Returns latest name.
     *
     * @return the get latest name result
     */
    String getLatestName();

    /**
     * Returns normalized phone.
     *
     * @return the get normalized phone result
     */
    String getNormalizedPhone();

    /**
     * Returns verification status.
     *
     * @return the get verification status result
     */
    String getVerificationStatus();

    /**
     * Returns first seen at.
     *
     * @return the get first seen at result
     */
    LocalDateTime getFirstSeenAt();

    /**
     * Returns last seen at.
     *
     * @return the get last seen at result
     */
    LocalDateTime getLastSeenAt();

    /**
     * Returns order count.
     *
     * @return the get order count result
     */
    Long getOrderCount();

    /**
     * Returns completed purchase count.
     *
     * @return the get completed purchase count result
     */
    Long getCompletedPurchaseCount();

    /**
     * Returns lifetime spend.
     *
     * @return the get lifetime spend result
     */
    BigDecimal getLifetimeSpend();

    /**
     * Returns last purchase at.
     *
     * @return the get last purchase at result
     */
    LocalDateTime getLastPurchaseAt();
}
