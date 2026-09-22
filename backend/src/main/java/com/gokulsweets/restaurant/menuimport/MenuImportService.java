package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.branchproduct.BranchProductRepository;
import com.gokulsweets.restaurant.category.Category;
import com.gokulsweets.restaurant.category.CategoryRepository;
import com.gokulsweets.restaurant.menuimport.dto.MenuImportErrorResponse;
import com.gokulsweets.restaurant.menuimport.dto.MenuImportResultResponse;
import com.gokulsweets.restaurant.menuimport.dto.MenuImportValidationResponse;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductRepository;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.tax.TaxCategory;
import com.gokulsweets.restaurant.tax.TaxCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuImportService {


    private final MenuExcelParser menuExcelParser;

    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final BranchProductRepository branchProductRepository;
    private final TaxCategoryRepository taxCategoryRepository;

    private final StaffAuthorizationService
            staffAuthorizationService;


    @Transactional(readOnly = true)
    public MenuImportValidationResponse validate(
            Long branchId,
            MultipartFile file
    ) {

        authorize(
                branchId
        );

        validateBranchExists(
                branchId
        );

        List<MenuImportRow> rows;

        try {

            rows =
                    menuExcelParser.parse(
                            file
                    );

        } catch (IllegalArgumentException exception) {

            return new MenuImportValidationResponse(
                    false,
                    0,
                    List.of(
                            new MenuImportErrorResponse(
                                    0,
                                    "file",
                                    exception.getMessage()
                            )
                    )
            );
        }

        List<MenuImportErrorResponse> errors =
                validateRows(
                        branchId,
                        rows
                );

        return new MenuImportValidationResponse(
                errors.isEmpty(),
                rows.size(),
                List.copyOf(
                        errors
                )
        );
    }


    @Transactional
    public MenuImportResultResponse importMenu(
            Long branchId,
            MultipartFile file
    ) {

        StaffUser staff =
                authorize(
                        branchId
                );

        Branch branch =
                validateBranchExists(
                        branchId
                );

        List<MenuImportRow> rows =
                menuExcelParser.parse(
                        file
                );

        List<MenuImportErrorResponse> errors =
                validateRows(
                        branchId,
                        rows
                );

        if (!errors.isEmpty()) {

            MenuImportErrorResponse first =
                    errors.getFirst();

            throw new IllegalArgumentException(
                    "Menu import failed validation. Row "
                            + first.row()
                            + ", column "
                            + first.column()
                            + ": "
                            + first.message()
            );
        }

        Map<String, Category> categoryByCode =
                categoryRepository
                        .findByCodeIn(
                                rows.stream()
                                        .map(
                                                row ->
                                                        normalizeCode(
                                                                row.categoryCode()
                                                        )
                                        )
                                        .collect(
                                                Collectors.toSet()
                                        )
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Category::getCode,
                                        Function.identity()
                                )
                        );

        Map<String, Product> productByCode =
                productRepository
                        .findByCodeIn(
                                rows.stream()
                                        .map(
                                                row ->
                                                        normalizeCode(
                                                                row.productCode()
                                                        )
                                        )
                                        .collect(
                                                Collectors.toSet()
                                        )
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Product::getCode,
                                        Function.identity()
                                )
                        );

        Map<String, TaxCategory> taxByCode =
                taxCategoryRepository
                        .findByCodeIn(
                                rows.stream()
                                        .map(
                                                row ->
                                                        normalizeCode(
                                                                row.taxCode()
                                                        )
                                        )
                                        .collect(
                                                Collectors.toSet()
                                        )
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        TaxCategory::getCode,
                                        Function.identity()
                                )
                        );


        int categoriesCreated = 0;
        int categoriesUpdated = 0;
        int productsCreated = 0;
        int productsUpdated = 0;
        int branchProductsCreated = 0;
        int branchProductsUpdated = 0;

        Map<String, MenuImportRow> categoryDefinition =
                firstCategoryRows(
                        rows
                );


        for (
                Map.Entry<String, MenuImportRow> entry :
                categoryDefinition.entrySet()
        ) {

            String code =
                    entry.getKey();

            MenuImportRow row =
                    entry.getValue();

            Category category =
                    categoryByCode.get(
                            code
                    );

            if (category == null) {

                category =
                        new Category();

                category.setCode(
                        code
                );

                applyCategoryFields(
                        category,
                        row
                );

                category =
                        categoryRepository.save(
                                category
                        );

                categoryByCode.put(
                        code,
                        category
                );

                categoriesCreated++;

            } else if (
                    categoryChanged(
                            category,
                            row
                    )
            ) {

                applyCategoryFields(
                        category,
                        row
                );

                categoryRepository.save(
                        category
                );

                categoriesUpdated++;
            }
        }


        for (
                MenuImportRow row :
                rows
        ) {

            String productCode =
                    normalizeCode(
                            row.productCode()
                    );

            String categoryCode =
                    normalizeCode(
                            row.categoryCode()
                    );

            String taxCode =
                    normalizeCode(
                            row.taxCode()
                    );

            Category category =
                    categoryByCode.get(
                            categoryCode
                    );

            TaxCategory taxCategory =
                    taxByCode.get(
                            taxCode
                    );

            Product product =
                    productByCode.get(
                            productCode
                    );

            if (product == null) {

                product =
                        new Product();

                product.setCode(
                        productCode
                );

                applyProductFields(
                        product,
                        category,
                        taxCategory,
                        row
                );

                product =
                        productRepository.save(
                                product
                        );

                productByCode.put(
                        productCode,
                        product
                );

                productsCreated++;

            } else if (
                    productChanged(
                            product,
                            category,
                            taxCategory,
                            row
                    )
            ) {

                applyProductFields(
                        product,
                        category,
                        taxCategory,
                        row
                );

                productRepository.save(
                        product
                );

                productsUpdated++;
            }


            Optional<BranchProduct> existingBranchProduct =
                    branchProductRepository
                            .findByBranchIdAndProductId(
                                    branch.getId(),
                                    product.getId()
                            );

            if (existingBranchProduct.isPresent()) {

                BranchProduct branchProduct =
                        existingBranchProduct.get();

                branchProduct.setPriceOverride(
                        row.branchPriceOverride()
                );

                branchProduct.setAvailable(
                        row.branchAvailable()
                );

                branchProduct.setDisplayOrder(
                        row.branchDisplayOrder()
                );

                branchProductRepository.save(
                        branchProduct
                );

                branchProductsUpdated++;

            } else {

                BranchProduct branchProduct =
                        new BranchProduct();

                branchProduct.setBranch(
                        branch
                );

                branchProduct.setProduct(
                        product
                );

                branchProduct.setPriceOverride(
                        row.branchPriceOverride()
                );

                branchProduct.setAvailable(
                        row.branchAvailable()
                );

                branchProduct.setDisplayOrder(
                        row.branchDisplayOrder()
                );

                branchProductRepository.save(
                        branchProduct
                );

                branchProductsCreated++;
            }
        }


        log.info(
                "Menu import completed: branchId={}, staffUserId={}, rows={}, categoriesCreated={}, categoriesUpdated={}, productsCreated={}, productsUpdated={}, branchProductsCreated={}, branchProductsUpdated={}",
                branchId,
                staff.getId(),
                rows.size(),
                categoriesCreated,
                categoriesUpdated,
                productsCreated,
                productsUpdated,
                branchProductsCreated,
                branchProductsUpdated
        );


        return new MenuImportResultResponse(
                true,
                rows.size(),
                categoriesCreated,
                categoriesUpdated,
                productsCreated,
                productsUpdated,
                branchProductsCreated,
                branchProductsUpdated
        );
    }


    private StaffUser authorize(
            Long branchId
    ) {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.MENU_MANAGE
                );

        staffAuthorizationService
                .requireBranchAccess(
                        branchId
                );

        return staffAuthorizationService
                .getCurrentStaff();
    }


    private Branch validateBranchExists(
            Long branchId
    ) {

        if (branchId == null) {

            throw new IllegalArgumentException(
                    "Branch ID is required."
            );
        }

        return branchRepository
                .findById(
                        branchId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Branch does not exist."
                                )
                );
    }


    private List<MenuImportErrorResponse> validateRows(
            Long branchId,
            List<MenuImportRow> rows
    ) {

        List<MenuImportErrorResponse> errors =
                new ArrayList<>();

        if (rows.isEmpty()) {

            errors.add(
                    new MenuImportErrorResponse(
                            0,
                            "file",
                            "The workbook does not contain any menu rows."
                    )
            );

            return errors;
        }


        StaffUser currentStaff =
                staffAuthorizationService
                        .getCurrentStaff();

        boolean owner =
                "OWNER_ADMIN".equals(
                        currentStaff
                                .getRole()
                                .getName()
                );


        Map<String, MenuImportRow> seenProducts =
                new HashMap<>();

        Map<String, MenuImportRow> seenCategories =
                new HashMap<>();


        for (
                MenuImportRow row :
                rows
        ) {

            String categoryCode =
                    normalizeCode(
                            row.categoryCode()
                    );

            String productCode =
                    normalizeCode(
                            row.productCode()
                    );

            String taxCode =
                    normalizeCode(
                            row.taxCode()
                    );


            validateCode(
                    row.excelRowNumber(),
                    "category_code",
                    categoryCode,
                    80,
                    errors
            );

            validateRequired(
                    row.excelRowNumber(),
                    "category_name",
                    row.categoryName(),
                    errors
            );

            validateCode(
                    row.excelRowNumber(),
                    "product_code",
                    productCode,
                    100,
                    errors
            );

            validateRequired(
                    row.excelRowNumber(),
                    "product_name",
                    row.productName(),
                    errors
            );

            validateCode(
                    row.excelRowNumber(),
                    "tax_code",
                    taxCode,
                    80,
                    errors
            );


            if (row.categoryDisplayOrder() < 0) {

                errors.add(
                        error(
                                row,
                                "category_display_order",
                                "Display order cannot be negative."
                        )
                );
            }

            if (row.branchDisplayOrder() < 0) {

                errors.add(
                        error(
                                row,
                                "branch_display_order",
                                "Display order cannot be negative."
                        )
                );
            }

            if (row.basePrice() == null
                    || row.basePrice()
                    .compareTo(
                            BigDecimal.ZERO
                    ) <= 0) {

                errors.add(
                        error(
                                row,
                                "base_price",
                                "Base price must be greater than zero."
                        )
                );
            }

            if (row.branchPriceOverride() != null
                    && row.branchPriceOverride()
                    .compareTo(
                            BigDecimal.ZERO
                    ) <= 0) {

                errors.add(
                        error(
                                row,
                                "branch_price_override",
                                "Branch price override must be greater than zero when supplied."
                        )
                );
            }

            validateWeightConfiguration(
                    row,
                    errors
            );


            MenuImportRow previousProduct =
                    seenProducts.putIfAbsent(
                            productCode,
                            row
                    );

            if (previousProduct != null) {

                errors.add(
                        error(
                                row,
                                "product_code",
                                "Duplicate product code "
                                        + productCode
                                        + ". It already appears in row "
                                        + previousProduct.excelRowNumber()
                                        + "."
                        )
                );
            }


            MenuImportRow previousCategory =
                    seenCategories.putIfAbsent(
                            categoryCode,
                            row
                    );

            if (previousCategory != null
                    && !sameCategoryDefinition(
                    previousCategory,
                    row
            )) {

                errors.add(
                        error(
                                row,
                                "category_code",
                                "Category "
                                        + categoryCode
                                        + " has conflicting values compared with row "
                                        + previousCategory.excelRowNumber()
                                        + "."
                        )
                );
            }
        }


        Set<String> categoryCodes =
                seenCategories.keySet();

        Set<String> productCodes =
                seenProducts.keySet();

        Set<String> taxCodes =
                rows.stream()
                        .map(
                                row ->
                                        normalizeCode(
                                                row.taxCode()
                                        )
                        )
                        .collect(
                                Collectors.toSet()
                        );


        Map<String, Category> existingCategories =
                categoryRepository
                        .findByCodeIn(
                                categoryCodes
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Category::getCode,
                                        Function.identity()
                                )
                        );


        Map<String, Product> existingProducts =
                productRepository
                        .findByCodeIn(
                                productCodes
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Product::getCode,
                                        Function.identity()
                                )
                        );


        Map<String, TaxCategory> existingTaxes =
                taxCategoryRepository
                        .findByCodeIn(
                                taxCodes
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        TaxCategory::getCode,
                                        Function.identity()
                                )
                        );


        for (
                MenuImportRow row :
                rows
        ) {

            String categoryCode =
                    normalizeCode(
                            row.categoryCode()
                    );

            String productCode =
                    normalizeCode(
                            row.productCode()
                    );

            String taxCode =
                    normalizeCode(
                            row.taxCode()
                    );


            TaxCategory taxCategory =
                    existingTaxes.get(
                            taxCode
                    );

            if (taxCategory == null) {

                errors.add(
                        error(
                                row,
                                "tax_code",
                                "Tax category "
                                        + taxCode
                                        + " does not exist."
                        )
                );

            } else if (
                    row.productActive()
                            && !taxCategory.isActive()
            ) {

                errors.add(
                        error(
                                row,
                                "tax_code",
                                "Active products must use an active tax category."
                        )
                );
            }


            Category existingCategory =
                    existingCategories.get(
                            categoryCode
                    );

            if (existingCategory == null) {

                categoryRepository
                        .findByNameIgnoreCase(
                                row.categoryName()
                                        .trim()
                        )
                        .filter(
                                category ->
                                        !category.getCode()
                                                .equals(
                                                        categoryCode
                                                )
                        )
                        .ifPresent(
                                category ->
                                        errors.add(
                                                error(
                                                        row,
                                                        "category_code",
                                                        "Category name already exists with code "
                                                                + category.getCode()
                                                                + ". Use the existing category code."
                                                )
                                        )
                        );

            } else if (
                    !owner
                            && categoryChanged(
                            existingCategory,
                            row
                    )
                            && branchProductRepository
                            .existsCategoryInOtherBranch(
                                    existingCategory.getId(),
                                    branchId
                            )
            ) {

                errors.add(
                        error(
                                row,
                                "category_code",
                                "This category is shared with another branch. Only OWNER_ADMIN can change its global category details."
                        )
                );
            }


            Product existingProduct =
                    existingProducts.get(
                            productCode
                    );

            if (existingProduct == null) {

                productRepository
                        .findByNameIgnoreCase(
                                row.productName()
                                        .trim()
                        )
                        .filter(
                                product ->
                                        !product.getCode()
                                                .equals(
                                                        productCode
                                                )
                        )
                        .ifPresent(
                                product ->
                                        errors.add(
                                                error(
                                                        row,
                                                        "product_code",
                                                        "Product name already exists with code "
                                                                + product.getCode()
                                                                + ". Use the existing product code."
                                                )
                                        )
                        );

            } else {

                Category targetCategory =
                        existingCategories.get(
                                categoryCode
                        );

                if (targetCategory == null) {

                    /*
                     * The target category will be created by this
                     * workbook. For change comparison we only need
                     * to know that the category code differs.
                     */
                    targetCategory =
                            new Category();

                    targetCategory.setCode(
                            categoryCode
                    );
                }

                if (!owner
                        && productChangedForValidation(
                        existingProduct,
                        targetCategory,
                        taxCategory,
                        row
                )
                        && branchProductRepository
                        .existsByProductIdAndBranchIdNot(
                                existingProduct.getId(),
                                branchId
                        )) {

                    errors.add(
                            error(
                                    row,
                                    "product_code",
                                    "This product is shared with another branch. Only OWNER_ADMIN can change its global product details. Branch price, availability and display order can still be changed."
                            )
                    );
                }
            }
        }


        return errors;
    }


    private Map<String, MenuImportRow> firstCategoryRows(
            List<MenuImportRow> rows
    ) {

        Map<String, MenuImportRow> result =
                new LinkedHashMap<>();

        for (
                MenuImportRow row :
                rows
        ) {

            result.putIfAbsent(
                    normalizeCode(
                            row.categoryCode()
                    ),
                    row
            );
        }

        return result;
    }


    private void applyCategoryFields(
            Category category,
            MenuImportRow row
    ) {

        category.setName(
                row.categoryName().trim()
        );

        category.setDescription(
                normalizeNullable(
                        row.categoryDescription()
                )
        );

        category.setDisplayOrder(
                row.categoryDisplayOrder()
        );

        category.setActive(
                row.categoryActive()
        );
    }


    private void applyProductFields(
            Product product,
            Category category,
            TaxCategory taxCategory,
            MenuImportRow row
    ) {

        product.setCategory(
                category
        );

        product.setTaxCategory(
                taxCategory
        );

        product.setName(
                row.productName().trim()
        );

        product.setDescription(
                normalizeNullable(
                        row.productDescription()
                )
        );

        product.setBasePrice(
                row.basePrice()
        );

        product.setSaleMode(
                row.saleMode()
        );

        product.setMinimumWeightGrams(
                row.minimumWeightGrams()
        );

        product.setWeightStepGrams(
                row.weightStepGrams()
        );

        product.setActive(
                row.productActive()
        );
    }


    private boolean categoryChanged(
            Category category,
            MenuImportRow row
    ) {

        return !Objects.equals(
                category.getName(),
                row.categoryName().trim()
        )
                || !Objects.equals(
                normalizeNullable(
                        category.getDescription()
                ),
                normalizeNullable(
                        row.categoryDescription()
                )
        )
                || !Objects.equals(
                category.getDisplayOrder(),
                row.categoryDisplayOrder()
        )
                || category.isActive()
                != row.categoryActive();
    }


    private boolean productChanged(
            Product product,
            Category category,
            TaxCategory taxCategory,
            MenuImportRow row
    ) {

        return productChangedForValidation(
                product,
                category,
                taxCategory,
                row
        );
    }


    private boolean productChangedForValidation(
            Product product,
            Category targetCategory,
            TaxCategory taxCategory,
            MenuImportRow row
    ) {

        String existingCategoryCode =
                product.getCategory()
                        .getCode();

        String targetCategoryCode =
                targetCategory.getCode();

        String existingTaxCode =
                product.getTaxCategory() == null
                        ? null
                        : product.getTaxCategory()
                        .getCode();

        String targetTaxCode =
                taxCategory == null
                        ? normalizeCode(
                        row.taxCode()
                )
                        : taxCategory.getCode();

        return !Objects.equals(
                existingCategoryCode,
                targetCategoryCode
        )
                || !Objects.equals(
                product.getName(),
                row.productName().trim()
        )
                || !Objects.equals(
                normalizeNullable(
                        product.getDescription()
                ),
                normalizeNullable(
                        row.productDescription()
                )
        )
                || product.getBasePrice()
                .compareTo(
                        row.basePrice()
                ) != 0
                || product.isActive()
                != row.productActive()
                || product.getSaleMode()
                != row.saleMode()
                || !Objects.equals(
                product.getMinimumWeightGrams(),
                row.minimumWeightGrams()
        )
                || !Objects.equals(
                product.getWeightStepGrams(),
                row.weightStepGrams()
        )
                || !Objects.equals(
                existingTaxCode,
                targetTaxCode
        );
    }

    private void validateWeightConfiguration(
            MenuImportRow row,
            List<MenuImportErrorResponse> errors
    ) {

        if (row.saleMode() == ProductSaleMode.WEIGHT) {

            if (
                    row.minimumWeightGrams() == null
                            || row.minimumWeightGrams() < 250
            ) {
                errors.add(
                        error(
                                row,
                                "minimum_weight_grams",
                                "WEIGHT products must have a minimum weight of at least 250 grams."
                        )
                );
            }

            if (
                    row.weightStepGrams() == null
                            || row.weightStepGrams() <= 0
            ) {
                errors.add(
                        error(
                                row,
                                "weight_step_grams",
                                "WEIGHT products must have a positive weight step."
                        )
                );
            }

            return;
        }

        if (
                row.minimumWeightGrams() != null
                        || row.weightStepGrams() != null
        ) {
            errors.add(
                    error(
                            row,
                            "sale_mode",
                            "UNIT products must leave weight columns blank."
                    )
            );
        }
    }


    private boolean sameCategoryDefinition(
            MenuImportRow first,
            MenuImportRow current
    ) {

        return Objects.equals(
                first.categoryName().trim(),
                current.categoryName().trim()
        )
                && Objects.equals(
                normalizeNullable(
                        first.categoryDescription()
                ),
                normalizeNullable(
                        current.categoryDescription()
                )
        )
                && first.categoryDisplayOrder()
                == current.categoryDisplayOrder()
                && first.categoryActive()
                == current.categoryActive();
    }


    private void validateCode(
            int row,
            String column,
            String code,
            int maxLength,
            List<MenuImportErrorResponse> errors
    ) {

        if (code.isBlank()) {

            errors.add(
                    new MenuImportErrorResponse(
                            row,
                            column,
                            "Code is required."
                    )
            );

            return;
        }

        if (code.length() > maxLength) {

            errors.add(
                    new MenuImportErrorResponse(
                            row,
                            column,
                            "Code must not exceed "
                                    + maxLength
                                    + " characters."
                    )
            );

            return;
        }

        if (!code.matches(
                "[A-Z0-9][A-Z0-9_]*"
        )) {

            errors.add(
                    new MenuImportErrorResponse(
                            row,
                            column,
                            "Use only uppercase letters, numbers and underscores."
                    )
            );
        }
    }


    private void validateRequired(
            int row,
            String column,
            String value,
            List<MenuImportErrorResponse> errors
    ) {

        if (value == null
                || value.isBlank()) {

            errors.add(
                    new MenuImportErrorResponse(
                            row,
                            column,
                            "Value is required."
                    )
            );
        }
    }


    private String normalizeCode(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }


    private String normalizeNullable(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value.trim();
    }


    private MenuImportErrorResponse error(
            MenuImportRow row,
            String column,
            String message
    ) {

        return new MenuImportErrorResponse(
                row.excelRowNumber(),
                column,
                message
        );
    }
}
