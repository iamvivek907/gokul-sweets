package com.gokulsweets.restaurant.printing.dto.admin;

/** Immutable admin print job counts response data contract. */
public record AdminPrintJobCountsResponse(
        long queued, long claimed, long printed, long failed, long permanentlyFailed) {}
