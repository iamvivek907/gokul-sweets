package com.gokulsweets.restaurant.category;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@SpringBootTest
@Transactional
class AdminCategoryIntegrationTest {
    @Autowired AdminCategoryController controller;
    @Autowired CategoryRepository repository;
    @Autowired CategoryService publicCategories;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StaffAuthorizationService authorization;

    @Test
    void createsAnEmptyCategoryVisibleToExistingPublicAndProductFlows() {
        long products = jdbc.queryForObject("SELECT count(*) FROM products", Long.class);
        String code =
                "CATEGORY_"
                        + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        var created =
                controller.create(
                        new AdminCategoryController.CreateCategory(
                                code.toLowerCase(Locale.ROOT),
                                "New breakfast",
                                "Vegetarian breakfast",
                                20));
        assertThat(created.code()).isEqualTo(code);
        assertThat(repository.findByCode(code)).isPresent();
        assertThat(publicCategories.getActiveCategories())
                .anySatisfy(category -> assertThat(category.id()).isEqualTo(created.id()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class))
                .isEqualTo(products);
        Long product =
                jdbc.queryForObject(
                        "INSERT INTO products(code,name,category_id,base_price,sale_mode) VALUES"
                                + " (?,?,?,100,'UNIT') RETURNING id",
                        Long.class,
                        "PRODUCT_" + UUID.randomUUID(),
                        "Test paratha",
                        created.id());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT category_id FROM products WHERE id=?", Long.class, product))
                .isEqualTo(created.id());
        verify(authorization).requirePermission(PermissionName.MENU_MANAGE);
    }
}
