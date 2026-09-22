package com.gokulsweets.restaurant.printing.dto.admin;

public record AdminPrintJobCountsResponse(

        long queued,

        long claimed,

        long printed,

        long failed,

        long permanentlyFailed
) {
}