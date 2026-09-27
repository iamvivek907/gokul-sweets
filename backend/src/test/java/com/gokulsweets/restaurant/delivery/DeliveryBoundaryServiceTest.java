package com.gokulsweets.restaurant.delivery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryBoundaryServiceTest {
    private static final List<DeliveryBoundaryService.Point> SQUARE = List.of(
            new DeliveryBoundaryService.Point(26.84, 80.93),
            new DeliveryBoundaryService.Point(26.84, 80.95),
            new DeliveryBoundaryService.Point(26.86, 80.95),
            new DeliveryBoundaryService.Point(26.86, 80.93));

    @Test
    void admitsInteriorAndExactBoundaryButRejectsNearbyUnservedAddress() {
        DeliveryBoundaryService.validate(SQUARE);
        assertThat(DeliveryBoundaryService.inside(SQUARE, new DeliveryBoundaryService.Point(26.85, 80.94))).isTrue();
        assertThat(DeliveryBoundaryService.inside(SQUARE, new DeliveryBoundaryService.Point(26.84, 80.94))).isTrue();
        assertThat(DeliveryBoundaryService.inside(SQUARE, new DeliveryBoundaryService.Point(26.85, 80.951))).isFalse();
    }

    @Test
    void rejectsSelfCrossingAndDegenerateBoundaries() {
        assertThatThrownBy(() -> DeliveryBoundaryService.validate(List.of(
                SQUARE.get(0), SQUARE.get(2), SQUARE.get(1), SQUARE.get(3))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryBoundaryService.validate(List.of(
                SQUARE.get(0), SQUARE.get(0), SQUARE.get(2))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryBoundaryService.validate(List.of(
                SQUARE.get(0), new DeliveryBoundaryService.Point(Double.NaN, 80.94), SQUARE.get(2))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
