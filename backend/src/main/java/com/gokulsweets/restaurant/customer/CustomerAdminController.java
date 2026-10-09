package com.gokulsweets.restaurant.customer;

import com.gokulsweets.restaurant.customer.dto.CustomerDetailResponse;
import com.gokulsweets.restaurant.customer.dto.CustomerDirectoryResponse;
import com.gokulsweets.restaurant.customer.dto.CustomerOrderHistoryResponse;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for customer admin operations. */
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class CustomerAdminController {

    private final CustomerDirectoryService customerDirectoryService;

    private final CustomerDetailService customerDetailService;

    /**
     * Returns customers.
     *
     * @param search the search
     * @param status the status
     * @param page the page
     * @param size the size
     * @return the get customers result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ResponseEntity<CustomerDirectoryResponse> getCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CustomerContactStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerAdminController.class,
                        "getCustomers(String,CustomerContactStatus,Integer,Integer)");
        try {
            return ResponseEntity.ok(
                    customerDirectoryService.getDirectory(search, status, page, size));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAdminController.class,
                    "getCustomers(String,CustomerContactStatus,Integer,Integer)");
        }
    }

    /**
     * Returns customer.
     *
     * @param customerId the customer id
     * @return the get customer result
     */
    @GetMapping("/{customerId}")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ResponseEntity<CustomerDetailResponse> getCustomer(@PathVariable Long customerId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerAdminController.class, "getCustomer(Long)");
        try {
            return ResponseEntity.ok(customerDetailService.getCustomer(customerId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerAdminController.class, "getCustomer(Long)");
        }
    }

    /**
     * Returns customer orders.
     *
     * @param customerId the customer id
     * @param page the page
     * @param size the size
     * @return the get customer orders result
     */
    @GetMapping("/{customerId}/orders")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ResponseEntity<CustomerOrderHistoryResponse> getCustomerOrders(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerAdminController.class, "getCustomerOrders(Long,Integer,Integer)");
        try {
            return ResponseEntity.ok(customerDetailService.getOrderHistory(customerId, page, size));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAdminController.class,
                    "getCustomerOrders(Long,Integer,Integer)");
        }
    }
}
