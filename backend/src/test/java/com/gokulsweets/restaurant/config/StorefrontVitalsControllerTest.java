package com.gokulsweets.restaurant.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StorefrontVitalsControllerTest {
    private final EnhancementProperties features = new EnhancementProperties();
    private final StorefrontVitalsController controller =
            new StorefrontVitalsController(features, new StorefrontPerformance());

    @Test
    void disabledDoesNotRecordAndMalformedOrHugeMetricsAreRejected() {
        var valid = new StorefrontVitalsController.Vital("LCP", 1800, "good", "menu");
        assertThat(controller.receive(valid).getStatusCode().value()).isEqualTo(204);
        features.setAccessibleOrderingV2(true);
        assertThat(controller.receive(valid).getStatusCode().value()).isEqualTo(204);
        assertThat(
                        controller
                                .receive(
                                        new StorefrontVitalsController.Vital(
                                                "LCP\nsecret", 200, "good", "menu"))
                                .getStatusCode()
                                .value())
                .isEqualTo(400);
        assertThat(
                        controller
                                .receive(
                                        new StorefrontVitalsController.Vital(
                                                "CLS", Double.NaN, "good", "cart"))
                                .getStatusCode()
                                .value())
                .isEqualTo(400);
        assertThat(
                        controller
                                .receive(
                                        new StorefrontVitalsController.Vital(
                                                "CLS", 4, "good", "/orders/123"))
                                .getStatusCode()
                                .value())
                .isEqualTo(400);
    }

    @Test
    void aggregateLoadingStagesHaveNoArbitraryLabelsOrQualityRating() {
        features.setAccessibleOrderingV2(true);
        assertThat(
                        controller
                                .receive(
                                        new StorefrontVitalsController.Vital(
                                                "MENU_PREVIEW", 200, "measured", "menu"))
                                .getStatusCode()
                                .value())
                .isEqualTo(204);
        assertThat(
                        controller
                                .receive(
                                        new StorefrontVitalsController.Vital(
                                                "MENU_PREVIEW", 200, "good", "menu"))
                                .getStatusCode()
                                .value())
                .isEqualTo(400);
        assertThat(
                        controller
                                .receive(
                                        new StorefrontVitalsController.Vital(
                                                "MENU_ORDER_123", 200, "measured", "menu"))
                                .getStatusCode()
                                .value())
                .isEqualTo(400);
        assertThat(
                        controller
                                .receive(
                                        new StorefrontVitalsController.Vital(
                                                null, 200, "measured", "menu"))
                                .getStatusCode()
                                .value())
                .isEqualTo(400);
    }

    @Test
    void funnelCountersRejectIdentifiersAndNonUnitValues() {
        features.setAccessibleOrderingV2(true);
        var names =
                new String[] {
                    "FUNNEL_MENU",
                    "FUNNEL_ADD",
                    "FUNNEL_CHECKOUT",
                    "FUNNEL_PAYMENT",
                    "FUNNEL_PAID",
                    "FUNNEL_FAILED",
                    "FUNNEL_EXPIRED"
                };
        for (var name : names) {
            var accepted = new StorefrontVitalsController.Vital(name, 1, "measured", "checkout");
            assertThat(controller.receive(accepted).getStatusCode().value()).isEqualTo(204);
            var invalid = new StorefrontVitalsController.Vital(name, 2, "measured", "checkout");
            assertThat(controller.receive(invalid).getStatusCode().value()).isEqualTo(400);
        }
        var privatePage =
                new StorefrontVitalsController.Vital("FUNNEL_PAID", 1, "measured", "/orders/123");
        assertThat(controller.receive(privatePage).getStatusCode().value()).isEqualTo(400);
    }
}
