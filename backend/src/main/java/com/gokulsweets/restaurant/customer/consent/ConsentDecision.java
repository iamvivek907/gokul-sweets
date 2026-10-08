package com.gokulsweets.restaurant.customer.consent;

import java.time.Instant;

/** Immutable consent decision data contract. */
public record ConsentDecision(boolean granted, String policyVersion, Instant recordedAt) {}
