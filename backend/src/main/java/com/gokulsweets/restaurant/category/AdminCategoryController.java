package com.gokulsweets.restaurant.category;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** Authenticated creation and paginated discovery of shared menu categories. */
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {
    private final CategoryRepository repository;
    private final StaffAuthorizationService authorization;

    /**
     * Lists shared categories in stable display order, including inactive entries.
     *
     * @param page zero-based page
     * @return a bounded page of categories
     */
    @GetMapping
    public CategoryPage list(@RequestParam(defaultValue = "0") int page) {
        final long started = MethodTiming.start(AdminCategoryController.class, "list(int)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            if (page < 0)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Page must be nonnegative.");
            var result =
                    repository.findAll(PageRequest.of(page, 50, Sort.by("displayOrder", "id")));
            return new CategoryPage(
                    result.getContent().stream().map(AdminCategory::from).toList(),
                    result.getTotalPages(),
                    result.getNumber());
        } finally {
            MethodTiming.finish(started, AdminCategoryController.class, "list(int)");
        }
    }

    /**
     * Creates an active category without creating products or stock. The database unique constraint
     * also rejects concurrent duplicate codes.
     *
     * @param input validated category definition
     * @return the saved shared category
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AdminCategory create(@Valid @RequestBody CreateCategory input) {
        final long started =
                MethodTiming.start(AdminCategoryController.class, "create(CreateCategory)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            String code = input.code().strip().toUpperCase(Locale.ROOT);
            if (repository.existsByCode(code))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Category code already exists. Choose that category or use another code.");
            var category = new Category();
            category.setCode(code);
            category.setName(input.name().strip());
            category.setDescription(
                    input.description() == null ? null : input.description().strip());
            category.setDisplayOrder(input.displayOrder());
            category.setActive(true);
            try {
                return AdminCategory.from(repository.saveAndFlush(category));
            } catch (DataIntegrityViolationException exception) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Category could not be created. Reload categories and check the code.");
            }
        } finally {
            MethodTiming.finish(started, AdminCategoryController.class, "create(CreateCategory)");
        }
    }

    /**
     * Category creation input. Codes are stable import identities; names are customer-facing.
     *
     * @param code unique ASCII business code, normalized to uppercase
     * @param name customer-facing name
     * @param description optional description
     * @param displayOrder nonnegative customer display priority
     */
    public record CreateCategory(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,79}")
                    String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotNull @Min(0) @Max(10000) Integer displayOrder) {}

    /**
     * Shared category summary.
     *
     * @param id persistent ID
     * @param code stable import identity
     * @param name display name
     * @param description optional description
     * @param displayOrder display priority
     * @param active whether available for new products
     */
    public record AdminCategory(
            Long id,
            String code,
            String name,
            String description,
            Integer displayOrder,
            boolean active) {
        /**
         * Maps persisted state without exposing persistence metadata.
         *
         * @param category persisted category
         * @return category summary
         */
        static AdminCategory from(Category category) {
            final long started =
                    MethodTiming.start(
                            AdminCategoryController.AdminCategory.class, "from(Category)");
            try {
                return new AdminCategory(
                        category.getId(),
                        category.getCode(),
                        category.getName(),
                        category.getDescription(),
                        category.getDisplayOrder(),
                        category.isActive());
            } finally {
                MethodTiming.finish(
                        started, AdminCategoryController.AdminCategory.class, "from(Category)");
            }
        }
    }

    /**
     * Bounded category list.
     *
     * @param content category summaries
     * @param totalPages total page count
     * @param number zero-based current page
     */
    public record CategoryPage(List<AdminCategory> content, int totalPages, int number) {}
}
