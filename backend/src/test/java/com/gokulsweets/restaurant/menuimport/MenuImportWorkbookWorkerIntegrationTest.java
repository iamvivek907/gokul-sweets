package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.security.*;
import com.gokulsweets.restaurant.staff.StaffUserRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real XLSX -> durable payload -> worker -> parser/service -> committed menu. */
@SpringBootTest(properties={"gokul.imports.async-enabled=true","gokul.jobs.worker-enabled=true","gokul.jobs.poll-ms=3600000"})
class MenuImportWorkbookWorkerIntegrationTest {
    @Autowired MenuImportJobs jobs;
    @Autowired MenuImportWorker worker;
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffUserRepository staff;
    @MockitoBean StaffAuthorizationService authorization;
    @MockitoBean StaffUserDetailsService users;
    long branch,staffId;String code;
    @BeforeEach void setup(){
        code="BOOK_"+UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        branch=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES(?,'Workbook worker') RETURNING id",Long.class,code);
        staffId=jdbc.queryForObject("INSERT INTO staff_users(username,password_hash,full_name,role_id) SELECT ?,'disabled','Workbook worker',id FROM roles WHERE name='OWNER_ADMIN' RETURNING id",Long.class,code);
        jdbc.update("INSERT INTO tax_categories(code,name) VALUES(?,'Workbook zero tax')",code);
        when(authorization.getCurrentStaff()).thenReturn(staff.findDetailedById(staffId).orElseThrow());
        when(users.loadUserByUsername(code)).thenReturn(User.withUsername(code).password("disabled").authorities("MENU_MANAGE").build());
    }
    @AfterEach void cleanup(){jdbc.update("DELETE FROM menu_import_jobs WHERE staff_id=?",staffId);}
    MockMultipartFile workbook(String price) throws Exception {
        String[] headers={"category_code","category_name","category_description","category_display_order","category_active","product_code","product_name","product_description","base_price","product_active","sale_mode","minimum_weight_grams","weight_step_grams","tax_code","branch_price_override","branch_available","branch_display_order"};
        String[] values={code,"Category "+code,"", "0","TRUE",code,"Sweet "+code,"Fresh sweet",price,"TRUE","UNIT","","",code,"","TRUE","0"};
        try(var book=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()){
            var sheet=book.createSheet("Menu_Upload");var header=sheet.createRow(0);var row=sheet.createRow(1);
            for(int i=0;i<headers.length;i++){header.createCell(i).setCellValue(headers[i]);row.createCell(i).setCellValue(values[i]);}
            book.write(bytes);return new MockMultipartFile("file","menu.xlsx",null,bytes.toByteArray());
        }
    }
    @Test void validWorkbookCommitsProductsAndJobResultTogether() throws Exception {
        var job=jobs.enqueue(branch,workbook("42.50"),"IMPORT");worker.process();
        var completed=jobs.get(branch,job.id());
        assertThat(completed.status()).as("worker error: %s",completed.error()).isEqualTo("SUCCEEDED");
        assertThat(jobs.get(branch,job.id()).result()).contains("productsCreated");
        assertThat(jdbc.queryForObject("SELECT p.base_price FROM products p JOIN branch_products bp ON bp.product_id=p.id WHERE bp.branch_id=? AND p.code=?",java.math.BigDecimal.class,branch,code)).isEqualByComparingTo("42.50");
        assertThat(jdbc.queryForObject("SELECT payload IS NULL FROM menu_import_jobs WHERE id=?",Boolean.class,job.id())).isTrue();
    }
    @Test void actualParserErrorRetainsRowAndColumnWithoutCreatingProducts() throws Exception {
        var job=jobs.enqueue(branch,workbook("not-a-price"),"IMPORT");worker.process();
        var failed=jobs.get(branch,job.id());assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.error()).contains("Row 2, column base_price");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE code=?",Long.class,code)).isZero();
    }
}
