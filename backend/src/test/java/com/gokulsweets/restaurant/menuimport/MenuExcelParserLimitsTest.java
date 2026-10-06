package com.gokulsweets.restaurant.menuimport;

import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MenuExcelParserLimitsTest {
    private final MenuExcelParser parser = new MenuExcelParser();

    @Test
    void rejectsCompressedExpansionBeforePoiLoadsIt() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));
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
            sheet.createRow(2001).createCell(0).setCellValue("SWEETS");
            workbook.write(bytes);
            var file = new MockMultipartFile("file", "menu.xlsx", null, bytes.toByteArray());
            assertThatThrownBy(() -> parser.parse(file)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2000 rows");
        }
    }
}
