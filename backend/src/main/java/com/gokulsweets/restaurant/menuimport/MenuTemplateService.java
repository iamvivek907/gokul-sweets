package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.tax.TaxCategory;
import com.gokulsweets.restaurant.tax.TaxCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuTemplateService {

    private final StaffAuthorizationService
            staffAuthorizationService;

    private final TaxCategoryRepository
            taxCategoryRepository;


    @Transactional(readOnly = true)
    public byte[] createTemplate(
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

        List<TaxCategory> taxCategories =
                taxCategoryRepository
                        .findByActiveTrueOrderByNameAsc();

        try (
                Workbook workbook =
                        new XSSFWorkbook();

                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            createMenuSheet(
                    workbook,
                    taxCategories
            );

            createReferenceSheet(
                    workbook,
                    taxCategories
            );

            workbook.write(
                    output
            );

            return output.toByteArray();

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Unable to generate menu template.",
                    exception
            );
        }
    }


    private void createMenuSheet(
            Workbook workbook,
            List<TaxCategory> taxCategories
    ) {

        Sheet sheet =
                workbook.createSheet(
                        MenuExcelParser.SHEET_NAME
                );

        String[] headers = {
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
        };

        Row headerRow =
                sheet.createRow(0);

        CellStyle headerStyle =
                createHeaderStyle(
                        workbook
                );

        for (
                int index = 0;
                index < headers.length;
                index++
        ) {

            Cell cell =
                    headerRow.createCell(
                            index
                    );

            cell.setCellValue(
                    headers[index]
            );

            cell.setCellStyle(
                    headerStyle
            );
        }


        Row sample =
                sheet.createRow(1);

        sample.createCell(0)
                .setCellValue("SWEETS");

        sample.createCell(1)
                .setCellValue("Sweets");

        sample.createCell(2)
                .setCellValue("Traditional Indian sweets");

        sample.createCell(3)
                .setCellValue(10);

        sample.createCell(4)
                .setCellValue(true);

        sample.createCell(5)
                .setCellValue("KAJU_KATLI");

        sample.createCell(6)
                .setCellValue("Kaju Katli");

        sample.createCell(7)
                .setCellValue("Premium cashew sweet");

        sample.createCell(8)
                .setCellValue(900);

        sample.createCell(9)
                .setCellValue(true);

        sample.createCell(10)
                .setCellValue("WEIGHT");

        sample.createCell(11)
                .setCellValue(250);

        sample.createCell(12)
                .setCellValue(50);

        sample.createCell(13)
                .setCellValue(
                        taxCategories.isEmpty()
                                ? "TAX_1"
                                : taxCategories
                                .getFirst()
                                .getCode()
                );

        sample.createCell(14)
                .setBlank();

        sample.createCell(15)
                .setCellValue(true);

        sample.createCell(16)
                .setCellValue(10);


        sheet.createFreezePane(
                0,
                1
        );

        for (
                int index = 0;
                index < headers.length;
                index++
        ) {

            sheet.autoSizeColumn(
                    index
            );

            int currentWidth =
                    sheet.getColumnWidth(
                            index
                    );

            sheet.setColumnWidth(
                    index,
                    Math.min(
                            Math.max(
                                    currentWidth + 1000,
                                    3500
                            ),
                            10000
                    )
            );
        }
    }


    private void createReferenceSheet(
            Workbook workbook,
            List<TaxCategory> taxCategories
    ) {

        Sheet sheet =
                workbook.createSheet(
                        "Reference_Data"
                );

        Row header =
                sheet.createRow(0);

        header.createCell(0)
                .setCellValue("tax_code");

        header.createCell(1)
                .setCellValue("tax_name");

        header.createCell(2)
                .setCellValue("cgst_rate");

        header.createCell(3)
                .setCellValue("sgst_rate");

        header.createCell(5)
                .setCellValue("sale_mode");

        header.createCell(6)
                .setCellValue("base_price_meaning");

        header.createCell(7)
                .setCellValue("weight_configuration");

        int rowIndex = 1;

        for (
                TaxCategory taxCategory :
                taxCategories
        ) {

            Row row =
                    sheet.createRow(
                            rowIndex++
                    );

            row.createCell(0)
                    .setCellValue(
                            taxCategory.getCode()
                    );

            row.createCell(1)
                    .setCellValue(
                            taxCategory.getName()
                    );

            row.createCell(2)
                    .setCellValue(
                            taxCategory
                                    .getCgstRate()
                                    .doubleValue()
                    );

            row.createCell(3)
                    .setCellValue(
                            taxCategory
                                    .getSgstRate()
                                    .doubleValue()
                    );
        }

        Row unitReference =
                sheet.getRow(1) != null
                        ? sheet.getRow(1)
                        : sheet.createRow(1);

        unitReference.createCell(5)
                .setCellValue("UNIT");

        unitReference.createCell(6)
                .setCellValue("Price for one item");

        unitReference.createCell(7)
                .setCellValue("Leave minimum and step blank");

        Row weightReference =
                sheet.getRow(2) != null
                        ? sheet.getRow(2)
                        : sheet.createRow(2);

        weightReference.createCell(5)
                .setCellValue("WEIGHT");

        weightReference.createCell(6)
                .setCellValue("Price for one kilogram");

        weightReference.createCell(7)
                .setCellValue("Minimum 250 g; recommended step 50 g");

        for (
                int index = 0;
                index < 8;
                index++
        ) {

            sheet.autoSizeColumn(
                    index
            );
        }
    }


    private CellStyle createHeaderStyle(
            Workbook workbook
    ) {

        CellStyle style =
                workbook.createCellStyle();

        Font font =
                workbook.createFont();

        font.setBold(true);

        style.setFont(
                font
        );

        return style;
    }
}
