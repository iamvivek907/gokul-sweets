package com.gokulsweets.restaurant.printing.dto.admin;

import java.util.List;

/** Immutable admin print job page response data contract. */
public record AdminPrintJobPageResponse(
        List<AdminPrintJobResponse> jobs, int page, int size, long totalElements, int totalPages) {}
