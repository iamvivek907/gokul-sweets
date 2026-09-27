package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.CustomerContactService;
import com.gokulsweets.restaurant.customer.identity.VerifiedOrderOwnership;
import com.gokulsweets.restaurant.inventory.service.OrderInventoryReservationService;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PreparationEligibilityStatus;
import com.gokulsweets.restaurant.order.service.PreparationEligibilityService;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.OrderCalculationService;
import com.gokulsweets.restaurant.order.service.OrderIdempotencyService;
import com.gokulsweets.restaurant.order.service.OrderNumberGenerator;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderData;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class DeliveryOrderCreationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired OrderRepository orders;
    @Autowired OrderIdempotencyService idempotency;
    @Autowired OrderNumberGenerator numbers;
    @Autowired PreparationEligibilityService preparationEligibility;

    @Test
    void retryReturnsSameOrderWithOneRiderAndInventoryReservation() {
        Clock ist = Clock.system(ZoneId.of("Asia/Kolkata"));
        String key = UUID.randomUUID().toString().substring(0, 8);
        Long branchId = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, ?) RETURNING id",
                Long.class, "DCO-" + key, "Delivery " + key);
        Long categoryId = jdbc.queryForObject("INSERT INTO categories(code, name) VALUES (?, ?) RETURNING id",
                Long.class, "DCO-C-" + key, "Category " + key);
        Long productId = jdbc.queryForObject("""
                INSERT INTO products(code, category_id, name, base_price) VALUES (?, ?, 'Sweet', 100)
                RETURNING id
                """, Long.class, "DCO-P-" + key, categoryId);
        Long branchProductId = jdbc.queryForObject("""
                INSERT INTO branch_products(branch_id, product_id) VALUES (?, ?) RETURNING id
                """, Long.class, branchId, productId);
        Long zoneId = jdbc.queryForObject("""
                INSERT INTO delivery_zones(branch_id, locality_key, postal_code, opens_at, closes_at, active, rider_paused)
                VALUES (?, 'hazratganj', '226001', '10:00', '20:00', true, false) RETURNING id
                """, Long.class, branchId);
        LocalDate date = LocalDate.now(ist).plusDays(1);
        Long windowId = jdbc.queryForObject("""
                INSERT INTO delivery_capacity_windows(zone_id, service_date, starts_at, ends_at, rider_capacity, paused)
                VALUES (?, ?, '11:00', '12:00', 1, false) RETURNING id
                """, Long.class, zoneId, date);
        var branch = em.find(Branch.class, branchId);
        var product = em.find(Product.class, productId);
        var branchProduct = em.find(BranchProduct.class, branchProductId);
        var items = List.of(new CreateOrderItemRequest(productId, 1, null));
        var quote = new DeliveryCapacityService.QuoteRequest(branchId, "Hazratganj", "226001", date,
                items, 26.85, 80.94);
        var window = new DeliveryCapacityService.Window(windowId, zoneId, date,
                LocalTime.of(11, 0), LocalTime.of(12, 0), 1, 0, false);
        var validated = new ValidatedOrderData(branch, null, null, List.of(new ValidatedOrderItem(
                product, branchProduct, ProductSaleMode.UNIT, 1, null)));
        var prepared = new DeliveryOrderPreparationService.Prepared(validated,
                new OrderCalculationService().calculateDelivery(validated.items()), window);
        var preparation = mock(DeliveryOrderPreparationService.class);
        when(preparation.prepare(quote, windowId)).thenReturn(prepared);
        var capacity = mock(DeliveryCapacityService.class);
        when(capacity.enabled()).thenReturn(true);
        when(capacity.quote(quote)).thenReturn(new DeliveryCapacityService.Quote(List.of(window), false, "Provisional"));
        var flags = new EnhancementProperties();
        flags.setDeliveryRiderHolds(true);
        flags.setDeliveryAddressBoundaries(true);
        flags.setDeliveryAcceptedQuote(true);
        flags.setDeliveryCheckout(true);
        flags.setDeliveryCapacity(true);
        flags.setDeliveryZones(true);
        flags.setDeliveryLocalityCheck(true);
        flags.setCustomerConsentControls(true);
        flags.setCustomerOtpIdentity(true);
        var rider = new DeliveryRiderHoldService(flags, capacity, jdbc, ist);
        var inventory = mock(OrderInventoryReservationService.class);
        var contacts = mock(CustomerContactService.class);
        var ownership = mock(VerifiedOrderOwnership.class);
        var accepted = new DeliveryAcceptedQuoteService(flags, preparation, ist);
        ReflectionTestUtils.setField(accepted, "signingKey", "delivery-quote-test-signing-key-at-least-32-characters");
        var service = new DeliveryOrderCreationService(flags, preparation, accepted, rider, inventory,
                idempotency, orders, numbers, contacts, ownership, ist);
        var draft = new DeliveryOrderCreationService.CreateRequest(quote, windowId,
                "Customer", "9999999999", "12 Main Road", null);
        var request = new DeliveryOrderCreationService.CreateRequest(quote, windowId,
                "Customer", "9999999999", "12 Main Road", accepted.preview(draft).token());
        String idempotencyKey = "delivery-create-" + key;

        var first = service.create(request, idempotencyKey, null);
        var retry = service.create(request, idempotencyKey, null);

        assertThat(retry.orderNumber()).isEqualTo(first.orderNumber());
        assertThat(first.totalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(first.id()).isPositive();
        assertThat(first.branchId()).isEqualTo(branchId);
        assertThat(first.windowId()).isEqualTo(windowId);
        assertThat(first.orderStatus()).isEqualTo(com.gokulsweets.restaurant.order.enums.OrderStatus.PENDING_PAYMENT);
        assertThat(first.createdAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT reserved_count FROM delivery_capacity_windows WHERE id = ?",
                Integer.class, windowId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE order_number = ?",
                Integer.class, first.orderNumber())).isEqualTo(1);
        var saved = orders.findByOrderNumber(first.orderNumber()).orElseThrow();
        assertThat(saved.getFulfillmentType()).isEqualTo(FulfillmentType.DELIVERY);
        assertThat(saved.getPickupSlot()).isNull();
        assertThat(saved.getItems()).hasSize(1);
        verify(inventory, times(1)).synchronizePendingDeliveryOrder(eq(saved), any());
        verify(preparation, times(2)).prepare(quote, windowId);

        // The paid-order transition uses the selected IST rider window rather than a pickup slot.
        saved.setOrderStatus(OrderStatus.CONFIRMED);
        orders.saveAndFlush(saved);
        assertThat(preparationEligibility.evaluate(saved, date.atTime(9, 59)).status())
                .isEqualTo(PreparationEligibilityStatus.SCHEDULED);
        assertThat(preparationEligibility.evaluate(saved, date.atTime(10, 0)).status())
                .isEqualTo(PreparationEligibilityStatus.ELIGIBLE);
        assertThat(preparationEligibility.evaluate(saved, date.atTime(11, 0)).status())
                .isEqualTo(PreparationEligibilityStatus.OVERDUE);
    }
}
