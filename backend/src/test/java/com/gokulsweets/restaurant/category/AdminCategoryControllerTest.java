package com.gokulsweets.restaurant.category;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

class AdminCategoryControllerTest {
    final CategoryRepository repository = mock(CategoryRepository.class);
    final StaffAuthorizationService authorization = mock(StaffAuthorizationService.class);
    final AdminCategoryController controller =
            new AdminCategoryController(repository, authorization);

    @Test
    void createsAnActiveSharedCategoryWithNormalizedIdentity() {
        when(repository.saveAndFlush(any()))
                .thenAnswer(
                        invocation -> {
                            Category category = invocation.getArgument(0);
                            category.setId(71L);
                            return category;
                        });
        var result =
                controller.create(
                        new AdminCategoryController.CreateCategory(
                                "breakfast", " Breakfast & Parathas ", " Morning favourites ", 2));
        assertThat(result.id()).isEqualTo(71L);
        assertThat(result.code()).isEqualTo("BREAKFAST");
        assertThat(result.name()).isEqualTo("Breakfast & Parathas");
        assertThat(result.description()).isEqualTo("Morning favourites");
        assertThat(result.displayOrder()).isEqualTo(2);
        assertThat(result.active()).isTrue();
        verify(authorization).requirePermission(PermissionName.MENU_MANAGE);
    }

    @Test
    void permissionDenialCannotReadOrWriteCategories() {
        doThrow(new AccessDeniedException("Denied"))
                .when(authorization)
                .requirePermission(PermissionName.MENU_MANAGE);
        assertThatThrownBy(() -> controller.list(0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(
                        () ->
                                controller.create(
                                        new AdminCategoryController.CreateCategory(
                                                "SWEETS", "Sweets", null, 0)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void bothExistingAndConcurrentDuplicateCodesReturnConflict() {
        var request = new AdminCategoryController.CreateCategory("sweets", "Sweets", null, 0);
        when(repository.existsByCode("SWEETS")).thenReturn(true);
        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        verify(repository, never()).saveAndFlush(any());
        when(repository.existsByCode("SWEETS")).thenReturn(false);
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));
        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void paginatesInStableOrderAndRejectsNegativePages() {
        when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        assertThat(controller.list(0).content()).isEmpty();
        var captor = org.mockito.ArgumentCaptor.forClass(PageRequest.class);
        verify(repository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getSort().getOrderFor("id")).isNotNull();
        assertThatThrownBy(() -> controller.list(-1))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void rejectsBlankNamesInvalidCodesAndOutOfRangeOrder() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(
                            validator.validate(
                                    new AdminCategoryController.CreateCategory(
                                            "SWEETS", "Sweets", null, 0)))
                    .isEmpty();
            assertThat(
                            validator.validate(
                                    new AdminCategoryController.CreateCategory(
                                            "bad code", "  ", null, -1)))
                    .hasSize(3);
            assertThat(
                            validator.validate(
                                    new AdminCategoryController.CreateCategory(
                                            "SWEETS", "Sweets", "a".repeat(501), 10001)))
                    .hasSize(2);
            assertThat(
                            validator.validate(
                                    new AdminCategoryController.CreateCategory(
                                            "SWEETS", "Sweets", null, null)))
                    .hasSize(1);
        }
    }
}
