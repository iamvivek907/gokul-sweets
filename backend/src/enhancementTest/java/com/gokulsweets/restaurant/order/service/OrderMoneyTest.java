package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.service.model.*;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import com.gokulsweets.restaurant.product.*;
import com.gokulsweets.restaurant.tax.TaxCategory;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class OrderMoneyTest {
    @Test void convenienceFeeIsInclusiveAndOnlyNormalPickupReceivesIt() {
        var product=new Product();product.setBasePrice(new BigDecimal("100"));
        var branch=new Branch();branch.setId(1L);branch.setPickupConvenienceFee(new BigDecimal("10"));branch.setPickupConvenienceFeeTaxRate(new BigDecimal("5"));
        var slot=new PickupSlot();slot.setId(1L);slot.setPriorityCharge(new BigDecimal("20"));
        var items=List.of(new ValidatedOrderItem(product,new BranchProduct(),ProductSaleMode.UNIT,1,null));
        var calculation=new OrderCalculationService();
        var normal=calculation.calculate(new ValidatedOrderData(branch,slot,PickupType.NORMAL,items));
        assertThat(normal.totalAmount()).isEqualByComparingTo("110.00");assertThat(normal.convenienceFeeTax()).isEqualByComparingTo("0.48");
        assertThat(calculation.calculate(new ValidatedOrderData(branch,slot,PickupType.PRIORITY,items)).totalAmount()).isEqualByComparingTo("120.00");
        assertThat(calculation.calculateDelivery(items).convenienceFee()).isZero();
    }
    @Test void unitAndWeightPricesKeepExistingTaxMath() {
        var tax = new TaxCategory(); tax.setCgstRate(new BigDecimal("2.5")); tax.setSgstRate(new BigDecimal("2.5"));
        tax.setIgstRate(new BigDecimal("99")); // Stored IGST must not be silently introduced into existing pickup calculations.
        var unit = new Product(); unit.setId(1L); unit.setBasePrice(new BigDecimal("15")); unit.setTaxCategory(tax);
        var weighted = new Product(); weighted.setId(2L); weighted.setBasePrice(new BigDecimal("300")); weighted.setTaxCategory(tax);
        var bp = new BranchProduct(); var bp2 = new BranchProduct();
        var branch = new Branch(); branch.setId(1L); var slot = new PickupSlot(); slot.setId(1L);
        var result = new OrderCalculationService().calculate(new ValidatedOrderData(branch, slot, PickupType.NORMAL, List.of(
                new ValidatedOrderItem(unit, bp, ProductSaleMode.UNIT, 10, null),
                new ValidatedOrderItem(weighted, bp2, ProductSaleMode.WEIGHT, 1, 250))));
        assertThat(result.items().get(0).lineTotal()).isEqualByComparingTo("157.50");
        assertThat(result.items().get(0).taxAmount()).isEqualByComparingTo("7.50");
        assertThat(result.items().get(1).lineTotal()).isEqualByComparingTo("78.75");
        assertThat(result.items().get(1).taxAmount()).isEqualByComparingTo("3.75");
        assertThat(result.totalAmount()).isEqualByComparingTo("236.25");
    }
    @Test void administratorTaxSwitchLeavesTaxRatesAndUnitPricesIntact() {
        var settings=org.mockito.Mockito.mock(com.gokulsweets.restaurant.tax.TaxCollectionSettings.class);
        org.mockito.Mockito.when(settings.enabled()).thenReturn(false);
        var tax=new TaxCategory();tax.setCgstRate(new BigDecimal("2.5"));tax.setSgstRate(new BigDecimal("2.5"));
        var product=new Product();product.setBasePrice(new BigDecimal("100"));product.setTaxCategory(tax);
        var branch=new Branch();branch.setId(1L);branch.setPickupConvenienceFee(new BigDecimal("10"));branch.setPickupConvenienceFeeTaxRate(new BigDecimal("5"));
        var slot=new PickupSlot();slot.setId(1L);
        var service=new OrderCalculationService();service.setTaxSettings(settings);
        var order=service.calculate(new ValidatedOrderData(branch,slot,PickupType.NORMAL,List.of(
            new ValidatedOrderItem(product,new BranchProduct(),ProductSaleMode.UNIT,1,null))));
        assertThat(order.items().getFirst().taxAmount()).isZero();
        assertThat(order.taxAmount()).isZero();assertThat(order.convenienceFeeTax()).isZero();
        assertThat(order.convenienceFeeTaxRate()).isZero();
        assertThat(order.totalAmount()).isEqualByComparingTo("110.00");
        var delivery=service.calculateDelivery(List.of(new ValidatedOrderItem(product,new BranchProduct(),ProductSaleMode.UNIT,1,null)));
        assertThat(delivery.taxAmount()).isZero();assertThat(delivery.items().getFirst().taxRate()).isZero();
        assertThat(delivery.totalAmount()).isEqualByComparingTo("100");
        org.mockito.Mockito.verify(settings,org.mockito.Mockito.times(2)).enabled();
    }

}
