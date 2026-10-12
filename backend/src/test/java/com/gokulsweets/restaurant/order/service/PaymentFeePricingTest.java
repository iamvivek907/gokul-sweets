package com.gokulsweets.restaurant.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.service.model.OrderCalculationResult;
import com.gokulsweets.restaurant.tax.TaxCollectionSettings;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

class PaymentFeePricingTest {
    BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @Test
    void disabledZeroPercentInclusiveTaxAndRounding() {
        var branch = new Branch();
        branch.setOnlinePaymentFeeRate(n("2"));
        branch.setOnlinePaymentFeeTaxRate(n("18"));
        var calculation = new OrderCalculationService();
        var price = new OrderCalculationResult(List.of(), n("1000"), n("0"), n("0"), n("1000"));
        assertThat(calculation.withPaymentFee(price, branch, n("0")).totalAmount())
                .isEqualByComparingTo("1000");
        branch.setOnlinePaymentFeeEnabled(true);
        var charged = calculation.withPaymentFee(price, branch, n("5"));
        assertThat(charged.paymentFee()).isEqualByComparingTo("20.10");
        assertThat(charged.paymentFeeTax()).isEqualByComparingTo("3.07");
        assertThat(charged.totalAmount()).isEqualByComparingTo("1020.10");
        var taxes = mock(TaxCollectionSettings.class);
        when(taxes.enabled()).thenReturn(false);
        calculation.setTaxSettings(taxes);
        assertThat(calculation.withPaymentFee(price, branch, n("0")).paymentFeeTax())
                .isEqualByComparingTo("0");
        assertThat(PaymentFeePricing.fee(n("0.25"), n("2"))).isEqualByComparingTo("0.01");
    }

    @Test
    void discountsRepriceFeeWithoutDiscountingConvenienceOrCompounding() {
        var order = new Order();
        order.setSubtotal(n("1000"));
        order.setTaxAmount(n("0"));
        order.setPriorityCharge(n("0"));
        order.setConvenienceFee(n("5"));
        order.setPaymentFeeRate(n("2"));
        order.setPaymentFeeTaxRate(n("18"));
        order.setRebateDiscountAmount(n("100"));
        PaymentFeePricing.reprice(order);
        assertThat(order.getPaymentFee()).isEqualByComparingTo("18.10");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("923.10");
        PaymentFeePricing.reprice(order);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("923.10");
        order.setRebateDiscountAmount(n("0"));
        PaymentFeePricing.reprice(order);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("1025.10");
    }
}
