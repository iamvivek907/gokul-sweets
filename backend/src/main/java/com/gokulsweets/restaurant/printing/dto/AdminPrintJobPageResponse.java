package com.gokulsweets.restaurant.printing.dto.admin;

import java.util.List;
import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintJobResponse;

public record AdminPrintJobPageResponse(

        List<AdminPrintJobResponse> jobs,

        int page,

        int size,

        long totalElements,

        int totalPages
) {
}