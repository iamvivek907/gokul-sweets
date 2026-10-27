package com.gokulsweets.restaurant.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.review.ReviewDtos.ProductSummaryRequest;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class CustomerReviewSummaryTest {
    @Test
    void summariesRetainPrecisionForCountWeightedSizeRatings() {
        var items = mock(ReviewItemRepository.class);
        var half = mock(ReviewItemRepository.ProductRatingSummaryView.class);
        var full = mock(ReviewItemRepository.ProductRatingSummaryView.class);
        when(half.getProductId()).thenReturn(10L);
        when(half.getAverageRating()).thenReturn(10.0 / 3);
        when(half.getRatingCount()).thenReturn(3L);
        when(full.getProductId()).thenReturn(11L);
        when(full.getAverageRating()).thenReturn(5.0);
        when(full.getRatingCount()).thenReturn(1L);
        when(items.summarizeProducts(Set.of(10L, 11L), ReviewStatus.PUBLISHED))
                .thenReturn(List.of(half, full));
        var service =
                new CustomerReviewService(
                        mock(OrderRepository.class),
                        mock(ReviewRepository.class),
                        items,
                        mock(EntityManager.class));

        var summaries = service.summarizeProducts(new ProductSummaryRequest(List.of(10L, 11L)));

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).averageRating()).isEqualTo(10.0 / 3);
        double combined =
                summaries.stream().mapToDouble(s -> s.averageRating() * s.ratingCount()).sum()
                        / summaries.stream().mapToLong(s -> s.ratingCount()).sum();
        assertThat(combined).isEqualTo(3.75);
    }
}
