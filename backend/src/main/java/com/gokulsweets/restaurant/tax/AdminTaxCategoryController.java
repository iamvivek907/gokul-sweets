package com.gokulsweets.restaurant.tax;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.tax.dto.TaxCategoryResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** HTTP endpoints for admin tax category operations. */
@RestController
@RequestMapping("/api/admin/tax-categories")
@RequiredArgsConstructor
public class AdminTaxCategoryController {

    private final TaxCategoryRepository repository;

    private final StaffAuthorizationService authorization;

    /**
     * Lists the operation.
     *
     * @return the list result
     */
    @GetMapping
    public List<TaxCategoryResponse> list() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminTaxCategoryController.class, "list()");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            return repository.findAll(Sort.by("name")).stream()
                    .map(TaxCategoryResponse::from)
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminTaxCategoryController.class, "list()");
        }
    }

    /**
     * Creates the operation.
     *
     * @param request the request
     * @return the create result
     */
    @PostMapping
    @Transactional
    public TaxCategoryResponse create(@Valid @RequestBody TaxRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminTaxCategoryController.class, "create(TaxRequest)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            return save(new TaxCategory(), request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminTaxCategoryController.class,
                    "create(TaxRequest)");
        }
    }

    /**
     * Updates the operation.
     *
     * @param id the id
     * @param request the request
     * @return the update result
     */
    @PutMapping("/{id}")
    @Transactional
    public TaxCategoryResponse update(
            @PathVariable Long id, @Valid @RequestBody TaxRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminTaxCategoryController.class, "update(Long,TaxRequest)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            return save(require(id), request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminTaxCategoryController.class,
                    "update(Long,TaxRequest)");
        }
    }

    /**
     * Actives the operation.
     *
     * @param id the id
     * @param request the request
     * @return the active result
     */
    @PatchMapping("/{id}/active")
    @Transactional
    public TaxCategoryResponse active(
            @PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminTaxCategoryController.class, "active(Long,ActiveRequest)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            TaxCategory category = require(id);
            // Deactivation removes selection, not existing product assignments or historical tax
            // snapshots.
            category.setActive(request.active());
            return TaxCategoryResponse.from(repository.save(category));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminTaxCategoryController.class,
                    "active(Long,ActiveRequest)");
        }
    }

    /**
     * Requires the operation.
     *
     * @param id the id
     * @return the require result
     */
    private TaxCategory require(Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminTaxCategoryController.class, "require(Long)");
        try {
            return repository
                    .findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Tax category not found."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminTaxCategoryController.class, "require(Long)");
        }
    }

    /**
     * Saves the operation.
     *
     * @param category the category
     * @param request the request
     * @return the save result
     */
    private TaxCategoryResponse save(TaxCategory category, TaxRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminTaxCategoryController.class, "save(TaxCategory,TaxRequest)");
        try {
            String code = request.code().trim().toUpperCase(Locale.ROOT);
            repository
                    .findByCode(code)
                    .filter(existing -> !existing.getId().equals(category.getId()))
                    .ifPresent(
                            existing -> {
                                throw new IllegalArgumentException("Tax code already exists.");
                            });
            if (request.cgstRate().add(request.sgstRate()).compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Combined CGST and SGST cannot exceed 100%.");
            }
            category.setCode(code);
            category.setName(request.name().trim());
            category.setHsnSacCode(
                    request.hsnSacCode() == null ? null : request.hsnSacCode().trim());
            category.setCgstRate(request.cgstRate());
            category.setSgstRate(request.sgstRate());
            category.setIgstRate(request.igstRate());
            category.setActive(request.active());
            return TaxCategoryResponse.from(repository.save(category));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminTaxCategoryController.class,
                    "save(TaxCategory,TaxRequest)");
        }
    }

    /**
     * Immutable active request data contract.
     *
     * @param active the active
     */
    public record ActiveRequest(@NotNull Boolean active) {}

    /**
     * Immutable tax request data contract.
     *
     * @param code the code
     * @param name the name
     * @param hsnSacCode the hsn sac code
     * @param cgstRate the cgst rate
     * @param sgstRate the sgst rate
     * @param igstRate the igst rate
     * @param active the active
     */
    public record TaxRequest(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 20) String hsnSacCode,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
                    BigDecimal cgstRate,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
                    BigDecimal sgstRate,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
                    BigDecimal igstRate,
            @NotNull Boolean active) {}
}
