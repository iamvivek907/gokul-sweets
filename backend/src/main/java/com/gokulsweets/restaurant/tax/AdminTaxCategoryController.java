package com.gokulsweets.restaurant.tax;

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

@RestController
@RequestMapping("/api/admin/tax-categories")
@RequiredArgsConstructor
public class AdminTaxCategoryController {
    private final TaxCategoryRepository repository;
    private final StaffAuthorizationService authorization;

    @GetMapping
    public List<TaxCategoryResponse> list() {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return repository.findAll(Sort.by("name")).stream().map(TaxCategoryResponse::from).toList();
    }

    @PostMapping
    @Transactional
    public TaxCategoryResponse create(@Valid @RequestBody TaxRequest request) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return save(new TaxCategory(), request);
    }

    @PutMapping("/{id}")
    @Transactional
    public TaxCategoryResponse update(@PathVariable Long id, @Valid @RequestBody TaxRequest request) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        return save(require(id), request);
    }

    @PatchMapping("/{id}/active")
    @Transactional
    public TaxCategoryResponse active(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        TaxCategory category = require(id);
        // Deactivation removes selection, not existing product assignments or historical tax snapshots.
        category.setActive(request.active());
        return TaxCategoryResponse.from(repository.save(category));
    }

    private TaxCategory require(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Tax category not found."));
    }

    private TaxCategoryResponse save(TaxCategory category, TaxRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        repository.findByCode(code).filter(existing -> !existing.getId().equals(category.getId()))
                .ifPresent(existing -> { throw new IllegalArgumentException("Tax code already exists."); });
        if (request.cgstRate().add(request.sgstRate()).compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Combined CGST and SGST cannot exceed 100%.");
        }
        category.setCode(code);
        category.setName(request.name().trim());
        category.setHsnSacCode(request.hsnSacCode() == null ? null : request.hsnSacCode().trim());
        category.setCgstRate(request.cgstRate());
        category.setSgstRate(request.sgstRate());
        category.setIgstRate(request.igstRate());
        category.setActive(request.active());
        return TaxCategoryResponse.from(repository.save(category));
    }

    public record ActiveRequest(@NotNull Boolean active) {}

    public record TaxRequest(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 20) String hsnSacCode,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal cgstRate,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal sgstRate,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal igstRate,
            @NotNull Boolean active
    ) {}
}
