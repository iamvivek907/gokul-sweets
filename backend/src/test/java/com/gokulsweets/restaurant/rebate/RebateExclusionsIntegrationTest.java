package com.gokulsweets.restaurant.rebate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.rebate.dto.CreateRebateRequest;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUserRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@SpringBootTest
@Transactional
class RebateExclusionsIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired RebateManagementService management;
    @Autowired RebateRepository rebates;
    @Autowired StaffUserRepository staffUsers;
    @Autowired RebateExclusionCatalogController catalog;
    @MockitoBean StaffAuthorizationService auth;

    @Test
    void migrationPersistsExclusionsAndCatalogIsBoundedAndBranchScoped() {
        String key = UUID.randomUUID().toString();
        long role =
                jdbc.queryForObject("SELECT id FROM roles WHERE name='OWNER_ADMIN'", Long.class);
        long owner =
                jdbc.queryForObject(
                        "INSERT INTO staff_users(username,password_hash,full_name,role_id) VALUES"
                                + " (?,'test-only','Offer owner',?) RETURNING id",
                        Long.class,
                        key,
                        role);
        when(auth.getCurrentStaff()).thenReturn(staffUsers.findByUsername(key).orElseThrow());
        long branch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES (?,?) RETURNING id",
                        Long.class,
                        key,
                        "Offer branch");
        long category =
                jdbc.queryForObject(
                        "INSERT INTO categories(code,name) VALUES (?,?) RETURNING id",
                        Long.class,
                        key,
                        "Offer drinks");
        long product = 0;
        for (int i = 0; i < 52; i++) {
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO products(code,name,base_price,category_id) VALUES"
                                    + " (?,?,20,?) RETURNING id",
                            Long.class,
                            key + i,
                            key + " item " + i,
                            category);
            jdbc.update(
                    "INSERT INTO branch_products(branch_id,product_id,available) VALUES (?,?,true)",
                    branch,
                    id);
            product = id;
        }
        var request =
                new CreateRebateRequest(
                        key,
                        "Food discount",
                        null,
                        RebateScope.GENERAL,
                        RebateVisibility.PUBLIC,
                        RebateType.PERCENTAGE,
                        new BigDecimal("10"),
                        new BigDecimal("300"),
                        new BigDecimal("50"),
                        null,
                        null,
                        branch,
                        LocalDateTime.now().minusDays(1),
                        LocalDateTime.now().plusDays(1),
                        Set.of(),
                        List.of(),
                        Set.of(product),
                        Set.of(category));
        long offer = management.create(request).id();
        entities.flush();
        entities.clear();
        var saved = management.getById(offer);
        assertThat(saved.excludedProductIds()).containsExactly(product);
        assertThat(saved.excludedCategoryIds()).containsExactly(category);
        var first = catalog.options(branch, key, 0, Set.of(product));
        assertThat(first.products()).hasSize(50);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.selectedProducts())
                .extracting(RebateExclusionCatalogController.Choice::id)
                .containsExactly(product);
        assertThat(catalog.options(branch, key, 1, null).products()).hasSize(2);
        long other =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES (?,?) RETURNING id",
                        Long.class,
                        key + "-other",
                        "Other");
        assertThat(catalog.options(other, key, 0, null).products()).isEmpty();
        rebates.deleteById(offer);
        entities.flush();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM rebate_excluded_products WHERE rebate_id=?",
                                Long.class,
                                offer))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM rebate_excluded_categories WHERE rebate_id=?",
                                Long.class,
                                offer))
                .isZero();
    }
}
