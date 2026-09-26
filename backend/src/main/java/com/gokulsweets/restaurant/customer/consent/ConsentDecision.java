package com.gokulsweets.restaurant.customer.consent;

import java.time.Instant;

public record ConsentDecision(boolean granted, String policyVersion, Instant recordedAt) { }
