package com.gokulsweets.restaurant.menuimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

class MenuExcelParserLimitsTest {
    private final MenuExcelParser parser = new MenuExcelParser();

    @Test
    void rejectsRenamedWorksheetEvenWhenPoiResolvesItsContentTypeAndRelationship()
            throws Exception {
        byte[] renamed = renamedWorksheet(false);
        // A real OPC package: this is loadable by POI despite the .dat worksheet name.
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(renamed))) {
            assertThat(workbook.getSheet("Menu_Upload").getLastRowNum()).isEqualTo(500);
        }
        assertRejectedBeforePoi(renamed);
    }

    @Test
    void rejectsRenamed500RowWorksheetWithExcessiveCellsBeforePoiAllocation() throws Exception {
        byte[] renamed = renamedWorksheet(true);
        assertThat(renamed.length).isLessThan(2 * 1024 * 1024);
        assertRejectedBeforePoi(renamed);
    }

    private void assertRejectedBeforePoi(byte[] bytes) {
        var opens = new java.util.concurrent.atomic.AtomicInteger();
        var file =
                new MockMultipartFile("file", "menu.xlsx", null, bytes) {
                    @Override
                    public java.io.InputStream getInputStream() throws java.io.IOException {
                        opens.incrementAndGet();
                        return super.getInputStream();
                    }
                };
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(MenuImportValidationException.class)
                .hasMessageContaining("Unsupported workbook part filename");
        // The second stream would construct XSSFWorkbook. Only preflight was entered.
        assertThat(opens.get()).isEqualTo(1);
    }

    private byte[] renamedWorksheet(boolean excessiveCells) throws Exception {
        var original = new ByteArrayOutputStream();
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Menu_Upload");
            for (int r = 0; r <= 500; r++) sheet.createRow(r).createCell(0).setCellValue("Sweet");
            workbook.write(original);
        }
        var result = new ByteArrayOutputStream();
        try (var input =
                        new java.util.zip.ZipInputStream(
                                new java.io.ByteArrayInputStream(original.toByteArray()));
                var output = new java.util.zip.ZipOutputStream(result)) {
            java.util.zip.ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                String xml =
                        new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (name.equals("[Content_Types].xml") || name.equals("xl/_rels/workbook.xml.rels"))
                    xml = xml.replace("sheet1.xml", "sheet1.dat");
                if (name.equals("xl/worksheets/sheet1.xml")) {
                    name = "xl/worksheets/sheet1.dat";
                    if (excessiveCells) {
                        // 500 rows alone do not bound POI objects: repeated cell references
                        // can allocate large graphs even though the compressed file is small.
                        var payload =
                                new StringBuilder(
                                        "<worksheet"
                                            + " xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><sheetData><row"
                                            + " r='1'/>");
                        for (int r = 2; r <= 501; r++) {
                            payload.append("<row r='").append(r).append("'>");
                            for (int c = 0; c < 120; c++)
                                payload.append("<c r='A")
                                        .append(r)
                                        .append("' t='inlineStr'><is><t>Sweet</t></is></c>");
                            payload.append("</row>");
                        }
                        xml = payload.append("</sheetData></worksheet>").toString();
                    }
                }
                output.putNextEntry(new java.util.zip.ZipEntry(name));
                output.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return result.toByteArray();
    }

    @Test
    void rejectsCompressedExpansionBeforePoiLoadsIt() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/payload.xml"));
            // Legal XML prolog whitespace reaches the byte limit before XML object/text limits.
            zip.write(
                    " "
                            .repeat(8 * 1024 * 1024 + 1)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.write("<worksheet/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file = new MockMultipartFile("file", "menu.xlsx", null, bytes.toByteArray());
        assertThatThrownBy(() -> parser.parse(file)).hasMessageContaining("expanded workbook");
    }

    @Test
    void rejectsOversizedFileBeforeOpeningWorkbook() {
        var file = new MockMultipartFile("file", "menu.xlsx", null, new byte[2 * 1024 * 1024 + 1]);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    void rejectsExcessiveSheetRangeIncludingSparseRows() throws Exception {
        try (var workbook = new XSSFWorkbook();
                var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Menu_Upload");
            sheet.createRow(0).createCell(0).setCellValue("category_code");
            sheet.createRow(501).createCell(0).setCellValue("SWEETS");
            workbook.write(bytes);
            var file = new MockMultipartFile("file", "menu.xlsx", null, bytes.toByteArray());
            assertThatThrownBy(() -> parser.parse(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("500 data rows");
        }
    }

    @Test
    void acceptsExactly500RowsWithTheReferenceSheet() throws Exception {
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
        String[] values = {
            "SWEETS",
            "Sweets",
            "",
            "0",
            "TRUE",
            "P",
            "Sweet",
            "Fresh sweet",
            "42.50",
            "TRUE",
            "UNIT",
            "",
            "",
            "ZERO",
            "",
            "TRUE",
            "0"
        };
        try (var workbook = new XSSFWorkbook();
                var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Menu_Upload");
            var header = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) header.createCell(c).setCellValue(headers[c]);
            for (int r = 1; r <= 500; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < values.length; c++) row.createCell(c).setCellValue(values[c]);
            }
            workbook.createSheet("Reference_Data")
                    .createRow(0)
                    .createCell(0)
                    .setCellValue("tax_code");
            workbook.write(bytes);
            assertThat(
                            parser.parse(
                                    new MockMultipartFile(
                                            "file", "menu.xlsx", null, bytes.toByteArray())))
                    .hasSize(500);
        }
    }

    private void rejectsXmlBeforePoi(String xml, String message) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        // Deliberately lacks POI's package metadata: the limit error proves rejection happened
        // before POI.
        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        new MockMultipartFile(
                                                "file", "menu.xlsx", null, bytes.toByteArray())))
                .isInstanceOf(MenuImportValidationException.class)
                .hasMessageContaining(message);
    }

    @Test
    void rejectsWideCellsBeforeWorkbookAllocation() throws Exception {
        rejectsXmlBeforePoi(
                "<worksheet><sheetData><row r='1'><c r='R1'/></row></sheetData></worksheet>",
                "17 template columns");
    }

    @Test
    void rejectsSparseCellReferenceWithoutTrustingRowMetadata() throws Exception {
        rejectsXmlBeforePoi(
                "<worksheet><sheetData><row r='1'><c r='A502'/></row></sheetData></worksheet>",
                "500 data rows");
    }

    @Test
    void boundsUnimportedWorksheetContent() throws Exception {
        try (var workbook = new XSSFWorkbook();
                var bytes = new ByteArrayOutputStream()) {
            workbook.createSheet("Menu_Upload");
            workbook.createSheet("Unimported").createRow(1).createCell(1000).setCellValue("unused");
            workbook.write(bytes);
            assertThatThrownBy(
                            () ->
                                    parser.parse(
                                            new MockMultipartFile(
                                                    "file",
                                                    "menu.xlsx",
                                                    null,
                                                    bytes.toByteArray())))
                    .hasMessageContaining("17 template columns");
        }
    }

    @Test
    void rejectsAdditionalWorksheets() throws Exception {
        try (var workbook = new XSSFWorkbook();
                var bytes = new ByteArrayOutputStream()) {
            workbook.createSheet("Menu_Upload");
            workbook.createSheet("Reference_Data");
            workbook.createSheet("Extra");
            workbook.write(bytes);
            assertThatThrownBy(
                            () ->
                                    parser.parse(
                                            new MockMultipartFile(
                                                    "file",
                                                    "menu.xlsx",
                                                    null,
                                                    bytes.toByteArray())))
                    .hasMessageContaining("two worksheets");
        }
    }

    @Test
    void boundsSharedStringTextBeforePoi() throws Exception {
        rejectsXmlBeforePoi(
                "<sst><si><t>" + "x".repeat(1024 * 1024 + 1) + "</t></si></sst>", "too much text");
    }

    @Test
    void boundsNonCellXmlObjectsBeforePoi() throws Exception {
        rejectsXmlBeforePoi(
                "<styleSheet>" + "<xf/>".repeat(60000) + "</styleSheet>", "too much XML content");
    }

    @Test
    void rejectsXmlEntitiesBeforePoi() throws Exception {
        rejectsXmlBeforePoi(
                "<!DOCTYPE worksheet [<!ENTITY x 'expanded'>]><worksheet>&x;</worksheet>",
                "XML declarations");
    }
}
