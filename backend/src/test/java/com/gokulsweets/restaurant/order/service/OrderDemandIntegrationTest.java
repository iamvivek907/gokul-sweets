package com.gokulsweets.restaurant.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.*;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;

@SpringBootTest(
        properties = {
            "gokul.features.admin-preparation-board=true",
            "gokul.notifications.staff.scheduler-enabled=false"
        })
@Transactional
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class OrderDemandIntegrationTest {
    @Autowired OrderDemandService demand;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StaffAuthorizationService staff;
    long branch, slot, product;
    final LocalDate date = LocalDate.of(2026, 10, 5);

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new TestingAuthenticationToken(
                                "manager", "test", "ORDER_VIEW", "REPORT_VIEW", "MENU_MANAGE"));
        var role = new Role();
        role.setName("MANAGER");
        var user = new StaffUser();
        user.setRole(role);
        when(staff.getCurrentStaff()).thenReturn(user);
        branch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES (?,'Demand test') RETURNING id",
                        Long.class,
                        "DEMAND-" + UUID.randomUUID());
        slot =
                jdbc.queryForObject(
                        "INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity)"
                                + " VALUES (?,'2026-10-05','18:00','18:30',100) RETURNING id",
                        Long.class,
                        branch);
        long category =
                jdbc.queryForObject(
                        "INSERT INTO categories(code,name) VALUES (?,'Demand') RETURNING id",
                        Long.class,
                        "DEMAND-" + UUID.randomUUID());
        long tax =
                jdbc.queryForObject(
                        "INSERT INTO tax_categories(code,name,cgst_rate,sgst_rate) VALUES"
                                + " (?,'Demand',0,0) RETURNING id",
                        Long.class,
                        "DEMAND-" + UUID.randomUUID());
        product =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " products(code,name,category_id,tax_category_id,base_price,sale_mode)"
                            + " VALUES (?,'=1+1',?,?,100,'WEIGHT') RETURNING id",
                        Long.class,
                        "DEMAND-" + UUID.randomUUID(),
                        category,
                        tax);
        jdbc.update(
                "INSERT INTO branch_products(branch_id,product_id) VALUES (?,?)", branch, product);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void add(String state, String mode, int qty, Integer grams) {
        long order =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " orders(order_number,branch_id,pickup_slot_id,customer_name,customer_phone,pickup_type,order_status,reservation_expires_at)"
                            + " VALUES (?,?,?,'Test','9876543210','NORMAL',?,CURRENT_TIMESTAMP)"
                            + " RETURNING id",
                        Long.class,
                        "DEMAND-" + UUID.randomUUID(),
                        branch,
                        slot,
                        state);
        jdbc.update(
                "INSERT INTO"
                    + " order_items(order_id,product_id,product_name,sale_mode,quantity,weight_grams,unit_price,tax_rate,tax_amount,line_total)"
                    + " VALUES (?,?,'=1+1',?,?,?,100,0,0,100)",
                order,
                product,
                mode,
                qty,
                grams);
    }

    @Test
    void totalsAndExcelCoverEveryPageWithoutCombiningUnitsOrExportingPhones() throws Exception {
        for (int i = 0; i < 124; i++) add("CONFIRMED", "WEIGHT", 1, 500);
        add("PREPARING", "UNIT", 3, null);
        add("PICKED_UP", "WEIGHT", 1, 1000);
        add("CANCELLED", "WEIGHT", 1, 2000);
        add("PENDING_PAYMENT", "WEIGHT", 1, 2000);
        var rows = demand.get(branch, date, date);
        assertThat(rows).hasSize(2);
        var weighted =
                rows.stream().filter(r -> r.saleMode().equals("WEIGHT")).findFirst().orElseThrow();
        assertThat(weighted.ordered()).isEqualTo(63000);
        assertThat(weighted.waiting()).isEqualTo(62000);
        assertThat(weighted.completed()).isEqualTo(1000);
        try (var book =
                new XSSFWorkbook(new ByteArrayInputStream(demand.export(branch, date, date)))) {
            assertThat(book.getSheet("Order lines").getLastRowNum()).isEqualTo(126);
            assertThat(book.getSheet("Daily quantities").getRow(1).getCell(3).getCellType())
                    .isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(book.getSheet("Order lines").getRow(0).getLastCellNum())
                    .isEqualTo((short) 9);
        }
    }

    @Test
    void exportsAndPoliciesRequireManagerRoleEvenWhenStaffHasReportPermission() {
        staff.getCurrentStaff().getRole().setName("COUNTER_STAFF");
        assertThatThrownBy(() -> demand.get(branch, date, date))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> demand.export(branch, date, date))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> demand.policy(branch, product, true))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void boundedDatesBranchScopeAndExplicitProductPolicy() {
        assertThatThrownBy(() -> demand.get(branch, date, date.plusDays(31)))
                .isInstanceOf(IllegalArgumentException.class);
        doThrow(new org.springframework.security.access.AccessDeniedException("Other branch"))
                .when(staff)
                .requireBranchAccess(999L);
        assertThatThrownBy(() -> demand.export(999, date, date)).hasMessage("Other branch");
        demand.policy(branch, product, true);
        assertThat(demand.policies(branch).getFirst().earlyPreparationAllowed()).isTrue();
        assertThatThrownBy(() -> demand.policy(branch, 9999999L, true))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
