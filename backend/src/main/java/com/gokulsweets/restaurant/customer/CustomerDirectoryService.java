package com.gokulsweets.restaurant.customer;

import com.gokulsweets.restaurant.customer.dto.CustomerDirectoryItemResponse;
import com.gokulsweets.restaurant.customer.dto.CustomerDirectoryResponse;
import com.gokulsweets.restaurant.observability.MethodTiming;
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

/** Coordinates customer directory operations. */
@Service
@RequiredArgsConstructor
public class CustomerDirectoryService {

    private static final int DEFAULT_PAGE_SIZE =
            AppConstant.CUSTOMER_DIRECTORY_SERVICE_DEFAULT_PAGE_SIZE;

    private static final int MAX_PAGE_SIZE = AppConstant.CUSTOMER_DIRECTORY_SERVICE_MAX_PAGE_SIZE;

    private final CustomerContactRepository customerContactRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    /**
     * Returns directory.
     *
     * @param search the search
     * @param status the status
     * @param page the page
     * @param size the size
     * @return the get directory result
     */
    @Transactional(readOnly = true)
    public CustomerDirectoryResponse getDirectory(
            String search, CustomerContactStatus status, Integer page, Integer size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerDirectoryService.class,
                        "getDirectory(String,CustomerContactStatus,Integer,Integer)");
        try {
            staffAuthorizationService.requirePermission(PermissionName.CUSTOMER_VIEW);
            int safePage = page == null ? 0 : Math.max(page, 0);
            int safeSize =
                    size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
            String normalizedSearch = normalizeSearch(search);
            String searchLike = normalizedSearch == null ? null : "%" + normalizedSearch + "%";
            Pageable pageable = PageRequest.of(safePage, safeSize);
            Page<CustomerDirectoryProjection> result =
                    customerContactRepository.searchDirectory(
                            normalizedSearch,
                            searchLike,
                            status == null ? null : status.name(),
                            pageable);
            return new CustomerDirectoryResponse(
                    result.getContent().stream().map(this::toResponse).toList(),
                    result.getNumber(),
                    result.getSize(),
                    result.getTotalElements(),
                    result.getTotalPages());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerDirectoryService.class,
                    "getDirectory(String,CustomerContactStatus,Integer,Integer)");
        }
    }

    /**
     * Tos response.
     *
     * @param row the row
     * @return the to response result
     */
    private CustomerDirectoryItemResponse toResponse(CustomerDirectoryProjection row) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerDirectoryService.class, "toResponse(CustomerDirectoryProjection)");
        try {
            BigDecimal lifetimeSpend =
                    row.getLifetimeSpend() == null ? BigDecimal.ZERO : row.getLifetimeSpend();
            return new CustomerDirectoryItemResponse(
                    row.getId(),
                    row.getLatestName(),
                    row.getNormalizedPhone(),
                    CustomerContactStatus.valueOf(row.getVerificationStatus()),
                    row.getFirstSeenAt(),
                    row.getLastSeenAt(),
                    valueOrZero(row.getOrderCount()),
                    valueOrZero(row.getCompletedPurchaseCount()),
                    lifetimeSpend,
                    row.getLastPurchaseAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerDirectoryService.class,
                    "toResponse(CustomerDirectoryProjection)");
        }
    }

    /**
     * Values or zero.
     *
     * @param value the value
     * @return the value or zero result
     */
    private long valueOrZero(Long value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerDirectoryService.class, "valueOrZero(Long)");
        try {
            return value == null ? 0L : value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerDirectoryService.class, "valueOrZero(Long)");
        }
    }

    /**
     * Normalizes search.
     *
     * @param value the value
     * @return the normalize search result
     */
    private String normalizeSearch(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerDirectoryService.class, "normalizeSearch(String)");
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim().toLowerCase(Locale.ROOT);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerDirectoryService.class,
                    "normalizeSearch(String)");
        }
    }
}
