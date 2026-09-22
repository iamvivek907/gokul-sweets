package com.gokulsweets.restaurant.customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CustomerDirectoryProjection {

    Long getId();

    String getLatestName();

    String getNormalizedPhone();

    String getVerificationStatus();

    LocalDateTime getFirstSeenAt();

    LocalDateTime getLastSeenAt();

    Long getOrderCount();

    Long getCompletedPurchaseCount();

    BigDecimal getLifetimeSpend();

    LocalDateTime getLastPurchaseAt();
}
