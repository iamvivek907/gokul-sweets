package com.gokulsweets.restaurant.reporting.dto;

import com.gokulsweets.restaurant.reporting.BusinessInsightSeverity;
import com.gokulsweets.restaurant.reporting.BusinessInsightType;

import java.math.BigDecimal;

/**
 * Immutable business insight response data contract.
 *
 * @param type the type
 * @param severity the severity
 * @param title the title
 * @param message the message
 * @param evidence the evidence
 * @param entityType the entity type
 * @param entityId the entity id
 * @param entityName the entity name
 * @param primaryMetric the primary metric
 * @param primaryMetricLabel the primary metric label
 */
public record BusinessInsightResponse(
        BusinessInsightType type,
        BusinessInsightSeverity severity,
        String title,
        String message,
        String evidence,
        String entityType,
        Long entityId,
        String entityName,
        BigDecimal primaryMetric,
        String primaryMetricLabel) {}
