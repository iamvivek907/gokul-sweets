package com.gokulsweets.restaurant.printing.dto.admin;

/**
 * Immutable admin print job counts response data contract.
 *
 * @param queued the queued
 * @param claimed the claimed
 * @param printed the printed
 * @param failed the failed
 * @param permanentlyFailed the permanently failed
 */
public record AdminPrintJobCountsResponse(
        long queued, long claimed, long printed, long failed, long permanentlyFailed) {}
