package com.gokulsweets.restaurant.customer;

import com.gokulsweets.restaurant.customer.dto.CustomerDirectoryItemResponse;
import com.gokulsweets.restaurant.customer.dto.CustomerDirectoryResponse;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomerDirectoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerContactRepository
            customerContactRepository;

    private final StaffAuthorizationService
            staffAuthorizationService;


    @Transactional(readOnly = true)
    public CustomerDirectoryResponse getDirectory(
            String search,
            CustomerContactStatus status,
            Integer page,
            Integer size
    ) {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.CUSTOMER_VIEW
                );

        int safePage =
                page == null
                        ? 0
                        : Math.max(
                        page,
                        0
                );

        int safeSize =
                size == null
                        ? DEFAULT_PAGE_SIZE
                        : Math.min(
                        Math.max(
                                size,
                                1
                        ),
                        MAX_PAGE_SIZE
                );

        String normalizedSearch =
                normalizeSearch(
                        search
                );

        String searchLike =
                normalizedSearch == null
                        ? null
                        : "%"
                        + normalizedSearch
                        + "%";

        Pageable pageable =
                PageRequest.of(
                        safePage,
                        safeSize
                );

        Page<CustomerDirectoryProjection> result =
                customerContactRepository
                        .searchDirectory(
                                normalizedSearch,
                                searchLike,
                                status == null
                                        ? null
                                        : status.name(),
                                pageable
                        );

        return new CustomerDirectoryResponse(
                result.getContent()
                        .stream()
                        .map(
                                this::toResponse
                        )
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }


    private CustomerDirectoryItemResponse toResponse(
            CustomerDirectoryProjection row
    ) {

        BigDecimal lifetimeSpend =
                row.getLifetimeSpend() == null
                        ? BigDecimal.ZERO
                        : row.getLifetimeSpend();

        return new CustomerDirectoryItemResponse(
                row.getId(),
                row.getLatestName(),
                row.getNormalizedPhone(),
                CustomerContactStatus.valueOf(
                        row.getVerificationStatus()
                ),
                row.getFirstSeenAt(),
                row.getLastSeenAt(),
                valueOrZero(
                        row.getOrderCount()
                ),
                valueOrZero(
                        row.getCompletedPurchaseCount()
                ),
                lifetimeSpend,
                row.getLastPurchaseAt()
        );
    }


    private long valueOrZero(
            Long value
    ) {

        return value == null
                ? 0L
                : value;
    }


    private String normalizeSearch(
            String value
    ) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            return null;
        }

        return value
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
