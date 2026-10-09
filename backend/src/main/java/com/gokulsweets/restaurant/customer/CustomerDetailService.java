package com.gokulsweets.restaurant.customer;

import com.gokulsweets.restaurant.customer.dto.CustomerDetailResponse;
import com.gokulsweets.restaurant.customer.dto.CustomerOrderHistoryItemResponse;
import com.gokulsweets.restaurant.customer.dto.CustomerOrderHistoryResponse;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Coordinates customer detail operations. */
@Service
@RequiredArgsConstructor
public class CustomerDetailService {

    private static final int DEFAULT_PAGE_SIZE =
            AppConstant.CUSTOMER_DETAIL_SERVICE_DEFAULT_PAGE_SIZE;

    private static final int MAX_PAGE_SIZE = AppConstant.CUSTOMER_DETAIL_SERVICE_MAX_PAGE_SIZE;

    private final CustomerContactRepository customerContactRepository;

    private final OrderRepository orderRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    /**
     * Returns customer.
     *
     * @param customerId the customer id
     * @return the get customer result
     */
    @Transactional(readOnly = true)
    public CustomerDetailResponse getCustomer(Long customerId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerDetailService.class, "getCustomer(Long)");
        try {
            requireCustomerView();
            CustomerDirectoryProjection row =
                    customerContactRepository
                            .findDirectoryItemById(requireCustomerId(customerId))
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Customer contact does not exist."));
            long completedPurchases = valueOrZero(row.getCompletedPurchaseCount());
            BigDecimal lifetimeSpend = moneyOrZero(row.getLifetimeSpend());
            BigDecimal averageOrderValue =
                    completedPurchases == 0
                            ? BigDecimal.ZERO
                            : lifetimeSpend.divide(
                                    BigDecimal.valueOf(completedPurchases),
                                    2,
                                    RoundingMode.HALF_UP);
            return new CustomerDetailResponse(
                    row.getId(),
                    row.getLatestName(),
                    row.getNormalizedPhone(),
                    CustomerContactStatus.valueOf(row.getVerificationStatus()),
                    row.getFirstSeenAt(),
                    row.getLastSeenAt(),
                    valueOrZero(row.getOrderCount()),
                    completedPurchases,
                    lifetimeSpend,
                    averageOrderValue,
                    row.getLastPurchaseAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerDetailService.class, "getCustomer(Long)");
        }
    }

    /**
     * Returns order history.
     *
     * @param customerId the customer id
     * @param page the page
     * @param size the size
     * @return the get order history result
     */
    @Transactional(readOnly = true)
    public CustomerOrderHistoryResponse getOrderHistory(
            Long customerId, Integer page, Integer size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerDetailService.class, "getOrderHistory(Long,Integer,Integer)");
        try {
            requireCustomerView();
            Long safeCustomerId = requireCustomerId(customerId);
            if (!customerContactRepository.existsById(safeCustomerId)) {
                throw new IllegalArgumentException("Customer contact does not exist.");
            }
            int safePage = page == null ? 0 : Math.max(page, 0);
            int safeSize =
                    size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
            Page<Order> result =
                    orderRepository.findByCustomerContactIdOrderByCreatedAtDesc(
                            safeCustomerId, PageRequest.of(safePage, safeSize));
            return new CustomerOrderHistoryResponse(
                    result.getContent().stream().map(this::toHistoryItem).toList(),
                    result.getNumber(),
                    result.getSize(),
                    result.getTotalElements(),
                    result.getTotalPages());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerDetailService.class,
                    "getOrderHistory(Long,Integer,Integer)");
        }
    }

    /**
     * Tos history item.
     *
     * @param order the order
     * @return the to history item result
     */
    private CustomerOrderHistoryItemResponse toHistoryItem(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerDetailService.class, "toHistoryItem(Order)");
        try {
            return new CustomerOrderHistoryItemResponse(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getCustomerOrderNumber(),
                    order.getBranch().getId(),
                    order.getBranch().getName(),
                    order.getPickupSlot().getSlotDate(),
                    order.getPickupSlot().getStartTime(),
                    order.getPickupSlot().getEndTime(),
                    order.getPickupType(),
                    order.getOrderStatus(),
                    order.getSubtotal(),
                    order.getTaxAmount(),
                    order.getRebateDiscountAmount(),
                    order.getTotalAmount(),
                    order.getCreatedAt());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerDetailService.class, "toHistoryItem(Order)");
        }
    }

    /**
     * Requires customer id.
     *
     * @param customerId the customer id
     * @return the require customer id result
     */
    private Long requireCustomerId(Long customerId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerDetailService.class, "requireCustomerId(Long)");
        try {
            if (customerId == null || customerId <= 0) {
                throw new IllegalArgumentException("Customer ID is required.");
            }
            return customerId;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerDetailService.class,
                    "requireCustomerId(Long)");
        }
    }

    /** Requires customer view. */
    private void requireCustomerView() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerDetailService.class, "requireCustomerView()");
        try {
            staffAuthorizationService.requirePermission(PermissionName.CUSTOMER_VIEW);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerDetailService.class,
                    "requireCustomerView()");
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
                MethodTiming.start(CustomerDetailService.class, "valueOrZero(Long)");
        try {
            return value == null ? 0L : value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerDetailService.class, "valueOrZero(Long)");
        }
    }

    /**
     * Money or zero.
     *
     * @param value the value
     * @return the money or zero result
     */
    private BigDecimal moneyOrZero(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerDetailService.class, "moneyOrZero(BigDecimal)");
        try {
            return value == null ? BigDecimal.ZERO : value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerDetailService.class,
                    "moneyOrZero(BigDecimal)");
        }
    }
}
