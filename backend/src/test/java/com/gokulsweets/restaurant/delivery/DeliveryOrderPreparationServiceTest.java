package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.OrderCalculationService;
import com.gokulsweets.restaurant.order.service.OrderValidationService;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import com.gokulsweets.restaurant.tax.TaxCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DeliveryOrderPreparationServiceTest {
    @Test
    void selectedWindowPricesValidatedWeightWithBranchOverrideAndTax() {
        var flags = new EnhancementProperties();
        flags.setDeliveryRiderHolds(true);
        flags.setDeliveryAddressBoundaries(true);
        var capacity = mock(DeliveryCapacityService.class);
        var branches = mock(BranchRepository.class);
        var validation = mock(OrderValidationService.class);
        var calculation = new OrderCalculationService();
        var taxSetting=mock(com.gokulsweets.restaurant.tax.TaxCollectionSettings.class);
        when(taxSetting.enabled()).thenReturn(true,false);
        calculation.setTaxSettings(taxSetting);
        var service = new DeliveryOrderPreparationService(flags, capacity, branches, validation, calculation, new DeliveryEconomicsService(flags, java.time.Clock.system(java.time.ZoneId.of("Asia/Kolkata"))));
        var branch = new Branch();
        branch.setId(7L);
        branch.setOnlinePaymentFeeEnabled(true);branch.setOnlinePaymentFeeRate(new BigDecimal("2.00"));branch.setOnlinePaymentFeeTaxRate(new BigDecimal("18.00"));
        var product = new Product();
        product.setId(8L);
        product.setBasePrice(new BigDecimal("1000.00"));
        var tax = new TaxCategory();
        tax.setCgstRate(new BigDecimal("2.50"));
        tax.setSgstRate(new BigDecimal("2.50"));
        product.setTaxCategory(tax);
        var branchProduct = new BranchProduct();
        branchProduct.setPriceOverride(new BigDecimal("800.00"));
        var item = new ValidatedOrderItem(product, branchProduct, ProductSaleMode.WEIGHT, 1, 500);
        var cart = List.of(new CreateOrderItemRequest(8L, null, 500));
        var date = LocalDate.of(2026, 10, 1);
        var request = new DeliveryCapacityService.QuoteRequest(7L, "Hazratganj", "226001", date,
                cart, 26.85, 80.94);
        var window = new DeliveryCapacityService.Window(25L, 15L, date, LocalTime.of(11, 0),
                LocalTime.of(12, 0), 2, 0, false);
        when(capacity.enabled()).thenReturn(true);
        when(capacity.quote(request)).thenReturn(new DeliveryCapacityService.Quote(List.of(window), false,
                "Provisional"));
        when(branches.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(branch));
        when(validation.validateCart(7L, cart)).thenReturn(List.of(item));

        var prepared = service.prepare(request, 25L);

        assertThat(prepared.price().subtotal()).isEqualByComparingTo("400.00");
        assertThat(prepared.price().taxAmount()).isEqualByComparingTo("20.00");
        assertThat(prepared.price().priorityCharge()).isEqualByComparingTo("0.00");
        assertThat(prepared.price().totalAmount()).isEqualByComparingTo("428.40");
        assertThat(prepared.price().paymentFeeTax()).isEqualByComparingTo("1.28");
        verify(taxSetting,times(1)).enabled();
        assertThat(prepared.validated().pickupSlot()).isNull();
        assertThatThrownBy(() -> service.prepare(request, 26L)).hasMessageContaining("unavailable");
        verify(validation, times(1)).validateCart(7L, cart);
        flags.setDeliveryRiderHolds(false);
        assertThatThrownBy(() -> service.prepare(request, 25L)).hasMessageContaining("disabled");
    }
}
