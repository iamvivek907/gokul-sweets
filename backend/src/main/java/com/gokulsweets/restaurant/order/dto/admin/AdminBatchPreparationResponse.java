package com.gokulsweets.restaurant.order.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

public record AdminBatchPreparationResponse(

        int requested,

        int attempted,

        int started,

        int skipped,

        List<AdminBatchPreparationItemResponse> results,

        LocalDateTime generatedAt

) {
}