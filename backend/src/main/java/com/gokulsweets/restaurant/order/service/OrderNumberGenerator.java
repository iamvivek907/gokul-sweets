package com.gokulsweets.restaurant.order.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.BASIC_ISO_DATE;

    public String generate() {

        String date =
                LocalDate.now()
                        .format(DATE_FORMAT);

        String randomPart =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 16)
                        .toUpperCase();

        return "GKS-" + date + "-" + randomPart;
    }
}