package com.gokulsweets.restaurant.printing.dto.admin;

import java.util.List;

/**
 * Immutable admin print job page response data contract.
 *
 * @param jobs the jobs
 * @param page the page
 * @param size the size
 * @param totalElements the total elements
 * @param totalPages the total pages
 */
public record AdminPrintJobPageResponse(
        List<AdminPrintJobResponse> jobs, int page, int size, long totalElements, int totalPages) {}
