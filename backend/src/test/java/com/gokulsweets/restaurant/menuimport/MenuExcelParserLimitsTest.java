package com.gokulsweets.restaurant.menuimport;

import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class MenuExcelParserLimitsTest {
    private final MenuExcelParser parser = new MenuExcelParser();

    @Test
    void rejectsCompressedExpansionBeforePoiLoadsIt() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/payload.bin"));
            zip.write(new byte[8*1024*1024+1]);zip.closeEntry();
        }
        var file=new MockMultipartFile("file","menu.xlsx",null,bytes.toByteArray());
        assertThatThrownBy(()->parser.parse(file)).hasMessageContaining("expanded workbook");
    }

    @Test
    void rejectsOversizedFileBeforeOpeningWorkbook() {
        var file = new MockMultipartFile("file", "menu.xlsx", null, new byte[2 * 1024 * 1024 + 1]);
        assertThatThrownBy(() -> parser.parse(file)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    void rejectsExcessiveSheetRangeIncludingSparseRows() throws Exception {
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Menu_Upload");
            sheet.createRow(0).createCell(0).setCellValue("category_code");
            sheet.createRow(501).createCell(0).setCellValue("SWEETS");
            workbook.write(bytes);
            var file = new MockMultipartFile("file", "menu.xlsx", null, bytes.toByteArray());
            assertThatThrownBy(() -> parser.parse(file)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("500 data rows");
        }
    }

    @Test
    void acceptsExactly500RowsWithTheReferenceSheet() throws Exception {
        String[] headers={"category_code","category_name","category_description","category_display_order","category_active","product_code","product_name","product_description","base_price","product_active","sale_mode","minimum_weight_grams","weight_step_grams","tax_code","branch_price_override","branch_available","branch_display_order"};
        String[] values={"SWEETS","Sweets","","0","TRUE","P","Sweet","Fresh sweet","42.50","TRUE","UNIT","","","ZERO","","TRUE","0"};
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("Menu_Upload");var header=sheet.createRow(0);
            for(int c=0;c<headers.length;c++)header.createCell(c).setCellValue(headers[c]);
            for(int r=1;r<=500;r++) {
                var row=sheet.createRow(r);
                for(int c=0;c<values.length;c++)row.createCell(c).setCellValue(values[c]);
            }
            workbook.createSheet("Reference_Data").createRow(0).createCell(0).setCellValue("tax_code");
            workbook.write(bytes);
            assertThat(parser.parse(new MockMultipartFile("file","menu.xlsx",null,bytes.toByteArray()))).hasSize(500);
        }
    }

    private void rejectsXmlBeforePoi(String xml, String message) throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
        }
        // Deliberately lacks POI's package metadata: the limit error proves rejection happened before POI.
        assertThatThrownBy(()->parser.parse(new MockMultipartFile("file","menu.xlsx",null,bytes.toByteArray())))
                .isInstanceOf(MenuImportValidationException.class).hasMessageContaining(message);
    }

    @Test void rejectsWideCellsBeforeWorkbookAllocation() throws Exception {
        rejectsXmlBeforePoi("<worksheet><sheetData><row r='1'><c r='R1'/></row></sheetData></worksheet>", "17 template columns");
    }
    @Test void rejectsSparseCellReferenceWithoutTrustingRowMetadata() throws Exception {
        rejectsXmlBeforePoi("<worksheet><sheetData><row r='1'><c r='A502'/></row></sheetData></worksheet>", "500 data rows");
    }
    @Test void boundsUnimportedWorksheetContent() throws Exception {
        try(var workbook=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()) {
            workbook.createSheet("Menu_Upload");
            workbook.createSheet("Unimported").createRow(1).createCell(1000).setCellValue("unused");
            workbook.write(bytes);
            assertThatThrownBy(()->parser.parse(new MockMultipartFile("file","menu.xlsx",null,bytes.toByteArray())))
                    .hasMessageContaining("17 template columns");
        }
    }
    @Test void rejectsAdditionalWorksheets() throws Exception {
        try(var workbook=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()) {
            workbook.createSheet("Menu_Upload");workbook.createSheet("Reference_Data");workbook.createSheet("Extra");
            workbook.write(bytes);
            assertThatThrownBy(()->parser.parse(new MockMultipartFile("file","menu.xlsx",null,bytes.toByteArray())))
                    .hasMessageContaining("two worksheets");
        }
    }
    @Test void boundsSharedStringTextBeforePoi() throws Exception {
        rejectsXmlBeforePoi("<sst><si><t>"+"x".repeat(1024*1024+1)+"</t></si></sst>","too much text");
    }
    @Test void boundsNonCellXmlObjectsBeforePoi() throws Exception {
        rejectsXmlBeforePoi("<styleSheet>"+"<xf/>".repeat(60000)+"</styleSheet>","too much XML content");
    }
    @Test void rejectsXmlEntitiesBeforePoi() throws Exception {
        rejectsXmlBeforePoi("<!DOCTYPE worksheet [<!ENTITY x 'expanded'>]><worksheet>&x;</worksheet>","XML declarations");
    }
}
