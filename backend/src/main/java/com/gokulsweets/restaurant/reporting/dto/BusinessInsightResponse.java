package com.gokulsweets.restaurant.reporting.dto;

import com.gokulsweets.restaurant.reporting.BusinessInsightSeverity;
import com.gokulsweets.restaurant.reporting.BusinessInsightType;

import java.math.BigDecimal;

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

        String primaryMetricLabel
) {
}
