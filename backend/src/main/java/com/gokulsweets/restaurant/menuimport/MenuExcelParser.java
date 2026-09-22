package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.product.ProductSaleMode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
public class MenuExcelParser {

    static final String SHEET_NAME =
            "Menu_Upload";

    private static final List<String> REQUIRED_HEADERS =
            List.of(
                    "category_code",
                    "category_name",
                    "category_description",
                    "category_display_order",
                    "category_active",
                    "product_code",
                    "product_name",
                    "product_description",
                    "base_price",
                    "product_active",
                    "sale_mode",
                    "minimum_weight_grams",
                    "weight_step_grams",
                    "tax_code",
                    "branch_price_override",
                    "branch_available",
                    "branch_display_order"
            );

    private final DataFormatter dataFormatter =
            new DataFormatter(Locale.ENGLISH);


    public List<MenuImportRow> parse(
            MultipartFile file
    ) {

        validateFile(file);

        try (
                InputStream inputStream =
                        file.getInputStream();

                Workbook workbook =
                        new XSSFWorkbook(
                                inputStream
                        )
        ) {

            Sheet sheet =
                    workbook.getSheet(
                            SHEET_NAME
                    );

            if (sheet == null) {

                throw new IllegalArgumentException(
                        "Workbook must contain a sheet named "
                                + SHEET_NAME
                                + "."
                );
            }

            Row headerRow =
                    sheet.getRow(0);

            if (headerRow == null) {

                throw new IllegalArgumentException(
                        "Menu_Upload sheet is missing its header row."
                );
            }

            Map<String, Integer> columns =
                    readHeaderMap(
                            headerRow
                    );

            for (
                    String requiredHeader :
                    REQUIRED_HEADERS
            ) {

                if (!columns.containsKey(
                        requiredHeader
                )) {

                    throw new IllegalArgumentException(
                            "Missing required column: "
                                    + requiredHeader
                    );
                }
            }

            List<MenuImportRow> rows =
                    new ArrayList<>();

            for (
                    int rowIndex = 1;
                    rowIndex <= sheet.getLastRowNum();
                    rowIndex++
            ) {

                Row row =
                        sheet.getRow(
                                rowIndex
                        );

                if (isBlankRow(
                        row,
                        columns
                )) {
                    continue;
                }

                int excelRow =
                        rowIndex + 1;

                rows.add(
                        new MenuImportRow(
                                excelRow,

                                text(
                                        row,
                                        columns,
                                        "category_code"
                                ),

                                text(
                                        row,
                                        columns,
                                        "category_name"
                                ),

                                nullableText(
                                        row,
                                        columns,
                                        "category_description"
                                ),

                                integer(
                                        row,
                                        columns,
                                        "category_display_order",
                                        excelRow
                                ),

                                bool(
                                        row,
                                        columns,
                                        "category_active",
                                        excelRow
                                ),

                                text(
                                        row,
                                        columns,
                                        "product_code"
                                ),

                                text(
                                        row,
                                        columns,
                                        "product_name"
                                ),

                                nullableText(
                                        row,
                                        columns,
                                        "product_description"
                                ),

                                decimal(
                                        row,
                                        columns,
                                        "base_price",
                                        excelRow,
                                        false
                                ),

                                bool(
                                        row,
                                        columns,
                                        "product_active",
                                        excelRow
                                ),

                                saleMode(
                                        row,
                                        columns,
                                        "sale_mode",
                                        excelRow
                                ),

                                nullableInteger(
                                        row,
                                        columns,
                                        "minimum_weight_grams",
                                        excelRow
                                ),

                                nullableInteger(
                                        row,
                                        columns,
                                        "weight_step_grams",
                                        excelRow
                                ),

                                text(
                                        row,
                                        columns,
                                        "tax_code"
                                ),

                                decimal(
                                        row,
                                        columns,
                                        "branch_price_override",
                                        excelRow,
                                        true
                                ),

                                bool(
                                        row,
                                        columns,
                                        "branch_available",
                                        excelRow
                                ),

                                integer(
                                        row,
                                        columns,
                                        "branch_display_order",
                                        excelRow
                                )
                        )
                );
            }

            return rows;

        } catch (IOException exception) {

            log.warn(
                    "Unable to read menu workbook: filename={}",
                    file.getOriginalFilename()
            );

            throw new IllegalArgumentException(
                    "Unable to read the uploaded Excel file.",
                    exception
            );
        }
    }


    private void validateFile(
            MultipartFile file
    ) {

        if (file == null
                || file.isEmpty()) {

            throw new IllegalArgumentException(
                    "Excel file is required."
            );
        }

        String filename =
                file.getOriginalFilename();

        if (filename == null
                || !filename.toLowerCase(
                Locale.ROOT
        ).endsWith(".xlsx")) {

            throw new IllegalArgumentException(
                    "Only .xlsx menu files are supported."
            );
        }
    }


    private Map<String, Integer> readHeaderMap(
            Row headerRow
    ) {

        Map<String, Integer> result =
                new HashMap<>();

        for (
                Cell cell :
                headerRow
        ) {

            String header =
                    dataFormatter
                            .formatCellValue(
                                    cell
                            )
                            .trim()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (!header.isBlank()) {

                result.put(
                        header,
                        cell.getColumnIndex()
                );
            }
        }

        return result;
    }


    private boolean isBlankRow(
            Row row,
            Map<String, Integer> columns
    ) {

        if (row == null) {
            return true;
        }

        for (
                String header :
                REQUIRED_HEADERS
        ) {

            Cell cell =
                    row.getCell(
                            columns.get(
                                    header
                            )
                    );

            if (cell != null
                    && !dataFormatter
                    .formatCellValue(
                            cell
                    )
                    .trim()
                    .isBlank()) {

                return false;
            }
        }

        return true;
    }


    private String text(
            Row row,
            Map<String, Integer> columns,
            String column
    ) {

        String value =
                nullableText(
                        row,
                        columns,
                        column
                );

        return value == null
                ? ""
                : value;
    }


    private String nullableText(
            Row row,
            Map<String, Integer> columns,
            String column
    ) {

        Cell cell =
                row.getCell(
                        columns.get(
                                column
                        )
                );

        if (cell == null) {
            return null;
        }

        String value =
                dataFormatter
                        .formatCellValue(
                                cell
                        )
                        .trim();

        return value.isBlank()
                ? null
                : value;
    }


    private int integer(
            Row row,
            Map<String, Integer> columns,
            String column,
            int excelRow
    ) {

        String value =
                text(
                        row,
                        columns,
                        column
                );

        try {

            BigDecimal decimal =
                    new BigDecimal(
                            value
                    );

            return decimal
                    .intValueExact();

        } catch (Exception exception) {

            throw invalidCell(
                    excelRow,
                    column,
                    "must be a whole number."
            );
        }
    }

    private Integer nullableInteger(
            Row row,
            Map<String, Integer> columns,
            String column,
            int excelRow
    ) {

        String value = nullableText(row, columns, column);

        if (value == null) {
            return null;
        }

        try {
            return new BigDecimal(value).intValueExact();
        } catch (Exception exception) {
            throw invalidCell(excelRow, column, "must be a whole number.");
        }
    }

    private ProductSaleMode saleMode(
            Row row,
            Map<String, Integer> columns,
            String column,
            int excelRow
    ) {

        String value = text(row, columns, column)
                .trim()
                .toUpperCase(Locale.ROOT);

        try {
            return ProductSaleMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalidCell(excelRow, column, "must be UNIT or WEIGHT.");
        }
    }


    private BigDecimal decimal(
            Row row,
            Map<String, Integer> columns,
            String column,
            int excelRow,
            boolean nullable
    ) {

        String value =
                nullableText(
                        row,
                        columns,
                        column
                );

        if (value == null) {

            if (nullable) {
                return null;
            }

            throw invalidCell(
                    excelRow,
                    column,
                    "is required."
            );
        }

        try {

            return new BigDecimal(
                    value.replace(
                            ",",
                            ""
                    )
            );

        } catch (NumberFormatException exception) {

            throw invalidCell(
                    excelRow,
                    column,
                    "must be a valid number."
            );
        }
    }


    private boolean bool(
            Row row,
            Map<String, Integer> columns,
            String column,
            int excelRow
    ) {

        String value =
                text(
                        row,
                        columns,
                        column
                )
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return switch (value) {

            case "true", "yes", "1" ->
                    true;

            case "false", "no", "0" ->
                    false;

            default ->
                    throw invalidCell(
                            excelRow,
                            column,
                            "must be TRUE or FALSE."
                    );
        };
    }


    private IllegalArgumentException invalidCell(
            int excelRow,
            String column,
            String reason
    ) {

        return new IllegalArgumentException(
                "Row "
                        + excelRow
                        + ", column "
                        + column
                        + " "
                        + reason
        );
    }
}
