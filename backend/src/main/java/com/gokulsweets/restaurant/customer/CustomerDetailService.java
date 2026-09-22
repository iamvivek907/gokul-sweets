package com.gokulsweets.restaurant.customer;

import com.gokulsweets.restaurant.customer.dto.CustomerDetailResponse;
import com.gokulsweets.restaurant.customer.dto.CustomerOrderHistoryItemResponse;
import com.gokulsweets.restaurant.customer.dto.CustomerOrderHistoryResponse;
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

@Service
@RequiredArgsConstructor
public class CustomerDetailService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerContactRepository
            customerContactRepository;

    private final OrderRepository
            orderRepository;

    private final StaffAuthorizationService
            staffAuthorizationService;


    @Transactional(readOnly = true)
    public CustomerDetailResponse getCustomer(
            Long customerId
    ) {

        requireCustomerView();

        CustomerDirectoryProjection row =
                customerContactRepository
                        .findDirectoryItemById(
                                requireCustomerId(
                                        customerId
                                )
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Customer contact does not exist."
                                        )
                        );

        long completedPurchases =
                valueOrZero(
                        row.getCompletedPurchaseCount()
                );

        BigDecimal lifetimeSpend =
                moneyOrZero(
                        row.getLifetimeSpend()
                );

        BigDecimal averageOrderValue =
                completedPurchases == 0
                        ? BigDecimal.ZERO
                        : lifetimeSpend.divide(
                        BigDecimal.valueOf(
                                completedPurchases
                        ),
                        2,
                        RoundingMode.HALF_UP
                );

        return new CustomerDetailResponse(
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
                completedPurchases,
                lifetimeSpend,
                averageOrderValue,
                row.getLastPurchaseAt()
        );
    }


    @Transactional(readOnly = true)
    public CustomerOrderHistoryResponse getOrderHistory(
            Long customerId,
            Integer page,
            Integer size
    ) {

        requireCustomerView();

        Long safeCustomerId =
                requireCustomerId(
                        customerId
                );

        if (
                !customerContactRepository
                        .existsById(
                                safeCustomerId
                        )
        ) {

            throw new IllegalArgumentException(
                    "Customer contact does not exist."
            );
        }

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

        Page<Order> result =
                orderRepository
                        .findByCustomerContactIdOrderByCreatedAtDesc(
                                safeCustomerId,
                                PageRequest.of(
                                        safePage,
                                        safeSize
                                )
                        );

        return new CustomerOrderHistoryResponse(
                result.getContent()
                        .stream()
                        .map(
                                this::toHistoryItem
                        )
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }


    private CustomerOrderHistoryItemResponse toHistoryItem(
            Order order
    ) {

        return new CustomerOrderHistoryItemResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getBranch()
                        .getId(),
                order.getBranch()
                        .getName(),
                order.getPickupSlot()
                        .getSlotDate(),
                order.getPickupSlot()
                        .getStartTime(),
                order.getPickupSlot()
                        .getEndTime(),
                order.getPickupType(),
                order.getOrderStatus(),
                order.getSubtotal(),
                order.getTaxAmount(),
                order.getRebateDiscountAmount(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }


    private Long requireCustomerId(
            Long customerId
    ) {

        if (
                customerId == null
                        ||
                        customerId <= 0
        ) {

            throw new IllegalArgumentException(
                    "Customer ID is required."
            );
        }

        return customerId;
    }


    private void requireCustomerView() {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.CUSTOMER_VIEW
                );
    }


    private long valueOrZero(
            Long value
    ) {

        return value == null
                ? 0L
                : value;
    }


    private BigDecimal moneyOrZero(
            BigDecimal value
    ) {

        return value == null
                ? BigDecimal.ZERO
                : value;
    }
}
