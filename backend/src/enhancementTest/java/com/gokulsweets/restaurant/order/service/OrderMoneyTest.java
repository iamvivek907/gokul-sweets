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
}
