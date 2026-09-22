package com.gokulsweets.restaurant.kot.service;

import org.springframework.stereotype.Component;

@Component
public class KotNumberGenerator {

    private static final String PREFIX =
            "KOT-";


    public String generate(
            Long orderId
    ) {

        if (
                orderId
                        ==
                        null
        ) {

            throw new IllegalArgumentException(
                    "Order ID is required to generate a KOT number."
            );
        }


        if (
                orderId
                        <=
                        0
        ) {

            throw new IllegalArgumentException(
                    "Order ID must be positive."
            );
        }


        return PREFIX
                +
                String.format(
                        "%08d",
                        orderId
                );
    }
}