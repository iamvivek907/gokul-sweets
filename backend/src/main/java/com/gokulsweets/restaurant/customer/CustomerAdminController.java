package com.gokulsweets.restaurant.customer;

import com.gokulsweets.restaurant.customer.dto.CustomerDetailResponse;
import com.gokulsweets.restaurant.customer.dto.CustomerDirectoryResponse;
import com.gokulsweets.restaurant.customer.dto.CustomerOrderHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class CustomerAdminController {

    private final CustomerDirectoryService
            customerDirectoryService;

    private final CustomerDetailService
            customerDetailService;


    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ResponseEntity<CustomerDirectoryResponse>
    getCustomers(

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            CustomerContactStatus status,

            @RequestParam(defaultValue = "0")
            Integer page,

            @RequestParam(defaultValue = "20")
            Integer size
    ) {

        return ResponseEntity.ok(
                customerDirectoryService
                        .getDirectory(
                                search,
                                status,
                                page,
                                size
                        )
        );
    }


    @GetMapping("/{customerId}")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ResponseEntity<CustomerDetailResponse>
    getCustomer(

            @PathVariable
            Long customerId
    ) {

        return ResponseEntity.ok(
                customerDetailService
                        .getCustomer(
                                customerId
                        )
        );
    }


    @GetMapping("/{customerId}/orders")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ResponseEntity<CustomerOrderHistoryResponse>
    getCustomerOrders(

            @PathVariable
            Long customerId,

            @RequestParam(defaultValue = "0")
            Integer page,

            @RequestParam(defaultValue = "20")
            Integer size
    ) {

        return ResponseEntity.ok(
                customerDetailService
                        .getOrderHistory(
                                customerId,
                                page,
                                size
                        )
        );
    }
}
