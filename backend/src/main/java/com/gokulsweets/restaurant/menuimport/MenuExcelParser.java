package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.observability.MethodTiming;
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

/** Backend menu excel parser contract and implementation. */
@Component
@Slf4j
public class MenuExcelParser {

    static final String SHEET_NAME = AppConstant.MENU_EXCEL_PARSER_SHEET_NAME;

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
                    "branch_display_order");

    private static final long MAX_FILE_BYTES = AppConstant.MENU_EXCEL_PARSER_MAX_FILE_BYTES;

    private static final int MAX_ROWS = MenuWorkbookLimits.MAX_DATA_ROWS;

    private final DataFormatter dataFormatter = new DataFormatter(Locale.ENGLISH);

    /**
     * Validates workbook limits.
     *
     * @param file the file
     */
    private void validateWorkbookLimits(MultipartFile file) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuExcelParser.class, "validateWorkbookLimits(MultipartFile)");
        try {
            try (var input = file.getInputStream()) {
                MenuWorkbookLimits.validate(input);
            } catch (IOException failure) {
                throw new MenuImportValidationException("Unable to read the Excel file.", failure);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuExcelParser.class,
                    "validateWorkbookLimits(MultipartFile)");
        }
    }

    /**
     * Parses the operation.
     *
     * @param file the file
     * @return the parse result
     */
    public List<MenuImportRow> parse(MultipartFile file) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuExcelParser.class, "parse(MultipartFile)");
        try {
            validateFile(file);
            validateWorkbookLimits(file);
            try (InputStream inputStream = file.getInputStream();
                    Workbook workbook = new XSSFWorkbook(inputStream)) {
                Sheet sheet = workbook.getSheet(SHEET_NAME);
                if (sheet == null) {
                    throw new MenuImportValidationException(
                            "Workbook must contain a sheet named " + SHEET_NAME + ".");
                }
                if (sheet.getLastRowNum() > MAX_ROWS) {
                    throw new MenuImportValidationException(
                            "Menu upload supports at most 500 data rows.");
                }
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    throw new MenuImportValidationException(
                            "Menu_Upload sheet is missing its header row.");
                }
                Map<String, Integer> columns = readHeaderMap(headerRow);
                for (String requiredHeader : REQUIRED_HEADERS) {
                    if (!columns.containsKey(requiredHeader)) {
                        throw new MenuImportValidationException(
                                "Missing required column: " + requiredHeader);
                    }
                }
                List<MenuImportRow> rows = new ArrayList<>();
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (isBlankRow(row, columns)) {
                        continue;
                    }
                    int excelRow = rowIndex + 1;
                    rows.add(
                            new MenuImportRow(
                                    excelRow,
                                    text(row, columns, "category_code"),
                                    text(row, columns, "category_name"),
                                    nullableText(row, columns, "category_description"),
                                    integer(row, columns, "category_display_order", excelRow),
                                    bool(row, columns, "category_active", excelRow),
                                    text(row, columns, "product_code"),
                                    text(row, columns, "product_name"),
                                    nullableText(row, columns, "product_description"),
                                    decimal(row, columns, "base_price", excelRow, false),
                                    bool(row, columns, "product_active", excelRow),
                                    saleMode(row, columns, "sale_mode", excelRow),
                                    nullableInteger(row, columns, "minimum_weight_grams", excelRow),
                                    nullableInteger(row, columns, "weight_step_grams", excelRow),
                                    text(row, columns, "tax_code"),
                                    decimal(row, columns, "branch_price_override", excelRow, true),
                                    bool(row, columns, "branch_available", excelRow),
                                    integer(row, columns, "branch_display_order", excelRow)));
                }
                return rows;
            } catch (IOException exception) {
                log.warn("Unable to read menu workbook: filename={}", file.getOriginalFilename());
                throw new MenuImportValidationException(
                        "Unable to read the uploaded Excel file.", exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuExcelParser.class, "parse(MultipartFile)");
        }
    }

    /**
     * Validates file.
     *
     * @param file the file
     */
    private void validateFile(MultipartFile file) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuExcelParser.class, "validateFile(MultipartFile)");
        try {
            if (file == null || file.isEmpty()) {
                throw new MenuImportValidationException("Excel file is required.");
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                throw new MenuImportValidationException("Menu Excel file must be 2 MB or smaller.");
            }
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                throw new MenuImportValidationException("Only .xlsx menu files are supported.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuExcelParser.class,
                    "validateFile(MultipartFile)");
        }
    }

    /**
     * Reads header map.
     *
     * @param headerRow the header row
     * @return the read header map result
     */
    private Map<String, Integer> readHeaderMap(Row headerRow) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuExcelParser.class, "readHeaderMap(Row)");
        try {
            Map<String, Integer> result = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = dataFormatter.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
                if (!header.isBlank()) {
                    result.put(header, cell.getColumnIndex());
                }
            }
            return result;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuExcelParser.class, "readHeaderMap(Row)");
        }
    }

    /**
     * Reports whether blank row.
     *
     * @param row the row
     * @param columns the columns
     * @return the is blank row result
     */
    private boolean isBlankRow(Row row, Map<String, Integer> columns) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuExcelParser.class, "isBlankRow(Row,Map<String,Integer>)");
        try {
            if (row == null) {
                return true;
            }
            for (String header : REQUIRED_HEADERS) {
                Cell cell = row.getCell(columns.get(header));
                if (cell != null && !dataFormatter.formatCellValue(cell).trim().isBlank()) {
                    return false;
                }
            }
            return true;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuExcelParser.class,
                    "isBlankRow(Row,Map<String,Integer>)");
        }
    }

    /**
     * Texts the operation.
     *
     * @param row the row
     * @param columns the columns
     * @param column the column
     * @return the text result
     */
    private String text(Row row, Map<String, Integer> columns, String column) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuExcelParser.class, "text(Row,Map<String,Integer>,String)");
        try {
            String value = nullableText(row, columns, column);
            return value == null ? "" : value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuExcelParser.class,
                    "text(Row,Map<String,Integer>,String)");
        }
    }

    /**
     * Nullables text.
     *
     * @param row the row
     * @param columns the columns
     * @param column the column
     * @return the nullable text result
     */
    private String nullableText(Row row, Map<String, Integer> columns, String column) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuExcelParser.class, "nullableText(Row,Map<String,Integer>,String)");
        try {
            Cell cell = row.getCell(columns.get(column));
            if (cell == null) {
                return null;
            }
            String value = dataFormatter.formatCellValue(cell).trim();
            return value.isBlank() ? null : value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuExcelParser.class,
                    "nullableText(Row,Map<String,Integer>,String)");
        }
    }

    /**
     * Integers the operation.
     *
     * @param row the row
     * @param columns the columns
     * @param column the column
     * @param excelRow the excel row
     * @return the integer result
     */
    private int integer(Row row, Map<String, Integer> columns, String column, int excelRow) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuExcelParser.class, "integer(Row,Map<String,Integer>,String,int)");
        try {
            String value = text(row, columns, column);
            try {
                BigDecimal decimal = new BigDecimal(value);
                return decimal.intValueExact();
            } catch (Exception exception) {
                throw invalidCell(excelRow, column, "must be a whole number.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuExcelParser.class,
                    "integer(Row,Map<String,Integer>,String,int)");
        }
    }

    /**
     * Nullables integer.
     *
     * @param row the row
     * @param columns the columns
     * @param column the column
     * @param excelRow the excel row
     * @return the nullable integer result
     */
    private Integer nullableInteger(
            Row row, Map<String, Integer> columns, String column, int excelRow) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuExcelParser.class,
                        "nullableInteger(Row,Map<String,Integer>,String,int)");
        try {
            String value = nullableText(row, columns, column);
            if (value == null) {
                return null;
            }
            try {
                return new BigDecimal(value).intValueExact();
            } catch (Exception exception) {
                throw invalidCell(excelRow, column, "must be a whole number.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuExcelParser.class,
                    "nullableInteger(Row,Map<String,Integer>,String,int)");
        }
    }

    /**
     * Sales mode.
     *
     * @param row the row
     * @param columns the columns
     * @param column the column
     * @param excelRow the excel row
     * @return the sale mode result
     */
    private ProductSaleMode saleMode(
            Row row, Map<String, Integer> columns, String column, int excelRow) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuExcelParser.class, "saleMode(Row,Map<String,Integer>,String,int)");
        try {
            String value = text(row, columns, column).trim().toUpperCase(Locale.ROOT);
            try {
                return ProductSaleMode.valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw invalidCell(excelRow, column, "must be UNIT or WEIGHT.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuExcelParser.class,
                    "saleMode(Row,Map<String,Integer>,String,int)");
        }
    }

    /**
     * Decimals the operation.
     *
     * @param row the row
     * @param columns the columns
     * @param column the column
     * @param excelRow the excel row
     * @param nullable the nullable
     * @return the decimal result
     */
    private BigDecimal decimal(
            Row row, Map<String, Integer> columns, String column, int excelRow, boolean nullable) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuExcelParser.class,
                        "decimal(Row,Map<String,Integer>,String,int,boolean)");
        try {
            String value = nullableText(row, columns, column);
            if (value == null) {
                if (nullable) {
                    return null;
                }
                throw invalidCell(excelRow, column, "is required.");
            }
            try {
                return new BigDecimal(value.replace(",", ""));
            } catch (NumberFormatException exception) {
                throw invalidCell(excelRow, column, "must be a valid number.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuExcelParser.class,
                    "decimal(Row,Map<String,Integer>,String,int,boolean)");
        }
    }

    /**
     * Bools the operation.
     *
     * @param row the row
     * @param columns the columns
     * @param column the column
     * @param excelRow the excel row
     * @return the bool result
     */
    private boolean bool(Row row, Map<String, Integer> columns, String column, int excelRow) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuExcelParser.class, "bool(Row,Map<String,Integer>,String,int)");
        try {
            String value = text(row, columns, column).trim().toLowerCase(Locale.ROOT);
            return switch (value) {
                case "true", "yes", "1" -> true;
                case "false", "no", "0" -> false;
                default -> throw invalidCell(excelRow, column, "must be TRUE or FALSE.");
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuExcelParser.class,
                    "bool(Row,Map<String,Integer>,String,int)");
        }
    }

    /**
     * Invalids cell.
     *
     * @param excelRow the excel row
     * @param column the column
     * @param reason the reason
     * @return the invalid cell result
     */
    private IllegalArgumentException invalidCell(int excelRow, String column, String reason) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuExcelParser.class, "invalidCell(int,String,String)");
        try {
            return new MenuImportValidationException(
                    "Row " + excelRow + ", column " + column + " " + reason);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuExcelParser.class,
                    "invalidCell(int,String,String)");
        }
    }
}
