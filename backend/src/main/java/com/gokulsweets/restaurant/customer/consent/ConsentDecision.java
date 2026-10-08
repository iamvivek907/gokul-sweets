package com.gokulsweets.restaurant.customer.consent;

import java.time.Instant;

/**
 * Immutable consent decision data contract.
 *
 * @param granted the granted
 * @param policyVersion the policy version
 * @param recordedAt the recorded at
 */
public record ConsentDecision(boolean granted, String policyVersion, Instant recordedAt) {}
