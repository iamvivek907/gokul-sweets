package com.gokulsweets.restaurant.delivery;

import static org.junit.jupiter.api.Assertions.*;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.service.model.OrderCalculationResult;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

class DeliveryEconomicsServiceTest {
    private final EnhancementProperties flags = new EnhancementProperties();
    private final DeliveryEconomicsService service =
            new DeliveryEconomicsService(
                    flags, java.time.Clock.system(java.time.ZoneId.of("Asia/Kolkata")));

    private static OrderCalculationResult price(String subtotal) {
        var amount = new BigDecimal(subtotal);
        return new OrderCalculationResult(
                List.of(), amount, BigDecimal.ZERO, BigDecimal.ZERO, amount);
    }

    private void set(String field, String value) {
        ReflectionTestUtils.setField(
                service, field, field.equals("version") ? value : new BigDecimal(value));
    }

    private void configure() {
        flags.setDeliveryEconomics(true);
        set("version", "pilot-2026-09");
        ReflectionTestUtils.setField(service, "costValidUntil", "2099-12-31");
        set("foodRate", "0.40");
        set("packaging", "10");
        set("labour", "10");
        set("waste", "2");
        set("paymentRate", "0.02");
        set("journey", "30");
        set("remedy", "5");
        set("feeFloor", "15");
        set("feeCap", "40");
        set("minimumContribution", "10");
    }

    @Test
    void disabledDoesNotChargeExistingDelivery() {
        assertEquals(BigDecimal.ZERO, service.assess(price("100")).fee());
    }

    @Test
    void fairFloorAndTaxExcludedContribution() {
        configure();
        var assessment = service.assess(price("100"));
        assertTrue(assessment.viable());
        assertEquals(new BigDecimal("15.00"), assessment.fee());
        assertEquals(new BigDecimal("16.00"), assessment.contribution());
    }

    @Test
    void tinyBasketCannotExceedFeeCap() {
        configure();
        var assessment = service.assess(price("10"));
        assertFalse(assessment.viable());
        assertEquals(new BigDecimal("0.00"), assessment.fee());
        assertTrue(assessment.alternative().contains("pickup"));
    }

    @Test
    void invalidRatesAndMissingVersionFailClosed() {
        configure();
        set("foodRate", "1.01");
        assertThrows(IllegalStateException.class, () -> service.assess(price("100")));
        set("foodRate", "0.4");
        set("version", "");
        assertThrows(IllegalStateException.class, () -> service.assess(price("100")));
    }
}
