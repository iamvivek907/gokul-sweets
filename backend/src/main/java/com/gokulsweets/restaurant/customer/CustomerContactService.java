package com.gokulsweets.restaurant.customer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerContactService {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Asia/Kolkata"
            );

    private final CustomerContactRepository
            customerContactRepository;


    /*
     * Returns a canonical Indian mobile representation:
     *
     * +919876543210
     *
     * Invalid / unsupported values return null. The order is
     * still allowed to retain its raw snapshot; it simply will
     * not be treated as a reliable customer grouping key.
     */
    public String normalizeIndianMobile(
            String rawPhone
    ) {

        if (
                rawPhone == null
                        ||
                        rawPhone.isBlank()
        ) {

            return null;
        }

        String digits =
                rawPhone.replaceAll(
                        "[^0-9]",
                        ""
                );

        String localNumber;

        if (
                digits.length() == 10
        ) {

            localNumber =
                    digits;

        } else if (
                digits.length() == 11
                        &&
                        digits.startsWith(
                                "0"
                        )
        ) {

            localNumber =
                    digits.substring(
                            1
                    );

        } else if (
                digits.length() == 12
                        &&
                        digits.startsWith(
                                "91"
                        )
        ) {

            localNumber =
                    digits.substring(
                            2
                    );

        } else {

            return null;
        }

        if (
                localNumber.length() != 10
                        ||
                        "6789".indexOf(
                                localNumber.charAt(
                                        0
                                )
                        ) < 0
        ) {

            return null;
        }

        return "+91"
                + localNumber;
    }


    @Transactional
    public CustomerContact resolveGuestContact(
            String normalizedPhone,
            String customerName
    ) {

        if (
                normalizedPhone == null
        ) {

            return null;
        }

        String latestName =
                normalizeName(
                        customerName
                );

        LocalDateTime seenAt =
                LocalDateTime.now(
                        BUSINESS_ZONE
                );

        customerContactRepository
                .upsertGuestContact(
                        normalizedPhone,
                        latestName,
                        seenAt
                );

        CustomerContact contact =
                customerContactRepository
                        .findByNormalizedPhone(
                                normalizedPhone
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Customer contact could not be resolved after upsert."
                                        )
                        );

        log.debug(
                "Guest customer contact resolved: customerContactId={}, status={}",
                contact.getId(),
                contact.getVerificationStatus()
        );

        return contact;
    }


    private String normalizeName(
            String value
    ) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            return null;
        }

        String normalized =
                value.trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );

        if (
                normalized.length() <= 150
        ) {

            return normalized;
        }

        return normalized.substring(
                0,
                150
        );
    }
}
