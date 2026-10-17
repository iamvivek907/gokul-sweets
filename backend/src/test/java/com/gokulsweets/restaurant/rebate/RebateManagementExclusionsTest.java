package com.gokulsweets.restaurant.rebate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.category.Category;
import com.gokulsweets.restaurant.category.CategoryRepository;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductRepository;
import com.gokulsweets.restaurant.rebate.dto.*;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

class RebateManagementExclusionsTest {
    final RebateRepository rebates = mock(RebateRepository.class);
    final ProductRepository products = mock(ProductRepository.class);
    final CategoryRepository categories = mock(CategoryRepository.class);
    final StaffAuthorizationService auth = mock(StaffAuthorizationService.class);
    final RebateManagementService service =
            new RebateManagementService(
                    rebates,
                    mock(RebateSlabRepository.class),
                    mock(RebateCustomerRepository.class),
                    mock(BranchRepository.class),
                    products,
                    categories,
                    auth);
    final LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0), end = start.plusDays(30);

    RebateManagementExclusionsTest() {
        var owner = mock(StaffUser.class, RETURNS_DEEP_STUBS);
        when(owner.getRole().getName()).thenReturn("OWNER_ADMIN");
        when(auth.getCurrentStaff()).thenReturn(owner);
        when(rebates.save(any()))
                .thenAnswer(
                        a -> {
                            Rebate r = a.getArgument(0);
                            r.setId(1L);
                            return r;
                        });
    }

    CreateRebateRequest create(Set<Long> p, Set<Long> c) {
        return new CreateRebateRequest(
                "FOOD",
                "Food offer",
                null,
                RebateScope.GENERAL,
                RebateVisibility.PUBLIC,
                RebateType.PERCENTAGE,
                new BigDecimal("10"),
                new BigDecimal("300"),
                new BigDecimal("50"),
                null,
                null,
                null,
                start,
                end,
                Set.of(),
                List.of(),
                p,
                c);
    }

    UpdateRebateRequest update(Set<Long> p, Set<Long> c) {
        return new UpdateRebateRequest(
                "FOOD",
                "Food offer",
                null,
                RebateScope.GENERAL,
                RebateVisibility.PUBLIC,
                RebateType.PERCENTAGE,
                new BigDecimal("10"),
                new BigDecimal("300"),
                new BigDecimal("50"),
                null,
                null,
                null,
                start,
                end,
                Set.of(),
                List.of(),
                p,
                c);
    }

    @Test
    void savesNamedEntityIdsAndRoundTripsThem() {
        when(products.findAllById(Set.of(20L))).thenReturn(List.of(new Product()));
        when(categories.findAllById(Set.of(2L))).thenReturn(List.of(new Category()));
        var response = service.create(create(Set.of(20L), Set.of(2L)));
        assertThat(response.excludedProductIds()).containsExactly(20L);
        assertThat(response.excludedCategoryIds()).containsExactly(2L);
    }

    @Test
    void rejectsUnknownAndInvalidIdsBeforeSaving() {
        assertThatThrownBy(() -> service.create(create(Set.of(999L), Set.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existing products");
        assertThatThrownBy(() -> service.create(create(Set.of(), Set.of(-1L))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(rebates, never()).save(any());
    }

    @Test
    void omittedFieldsPreserveRulesAndExplicitEmptySetsClearThem() {
        var r = new Rebate();
        r.setId(1L);
        r.setCreatedBy(auth.getCurrentStaff());
        r.getExcludedProductIds().add(20L);
        r.getExcludedCategoryIds().add(2L);
        when(rebates.findById(1L)).thenReturn(Optional.of(r));
        var kept = service.update(1L, update(null, null));
        assertThat(kept.excludedProductIds()).containsExactly(20L);
        assertThat(kept.excludedCategoryIds()).containsExactly(2L);
        var cleared = service.update(1L, update(Set.of(), Set.of()));
        assertThat(cleared.excludedProductIds()).isEmpty();
        assertThat(cleared.excludedCategoryIds()).isEmpty();
    }
}
