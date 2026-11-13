package com.gokulsweets.restaurant.menu;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.menu.workspace.MenuAppearanceController;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@SpringBootTest
@Transactional
class MenuAppearanceIntegrationTest {
    @Autowired MenuAppearanceController appearance;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StaffAuthorizationService staff;

    @Test
    void draftIsPrivateUntilPublishAndStaleVersionsAreRejected() {
        var actor = new StaffUser();
        actor.setUsername("test");
        when(staff.getCurrentStaff()).thenReturn(actor);
        long b =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES (?, 'Appearance') RETURNING id",
                        Long.class,
                        UUID.randomUUID().toString());
        var banner =
                new MenuAppearanceController.Banner(
                        "sweet",
                        "Sweet moments",
                        "मिठास",
                        "Browse sweets",
                        "",
                        "",
                        null,
                        true,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new MenuAppearanceController.Frame(50, 50, 100, "COVER"));
        var category =
                jdbc.queryForObject(
                        "INSERT INTO categories(code,name) VALUES (?, 'Sweets') RETURNING id",
                        Long.class,
                        UUID.randomUUID().toString());
        var products = new ArrayList<Long>();
        for (int i = 0; i < 3; i++) {
            products.add(
                    jdbc.queryForObject(
                            "INSERT INTO products(code,name,category_id,base_price,sale_mode)"
                                    + " VALUES (?, ?, ?, 40, 'UNIT') RETURNING id",
                            Long.class,
                            UUID.randomUUID().toString(),
                            "Pairing dish " + i,
                            category));
        }
        jdbc.update(
                "INSERT INTO branch_products(branch_id,product_id) VALUES (?,?),(?,?)",
                b,
                products.get(0),
                b,
                products.get(1));
        var pairing = new MenuAppearanceController.Pairing(products.get(0), products.get(1));
        var config =
                new MenuAppearanceController.Config(List.of(banner), List.of(), List.of(pairing));
        appearance.save(b, new MenuAppearanceController.Input(0, config, false));
        assertThat(appearance.live(b).banners()).isEmpty();
        assertThat(appearance.live(b).pairings()).isEmpty();
        appearance.save(b, new MenuAppearanceController.Input(1, config, true));
        assertThat(appearance.live(b).banners()).containsExactly(banner);
        assertThat(appearance.live(b).pairings()).containsExactly(pairing);
        var outsideBranch =
                new MenuAppearanceController.Config(
                        List.of(banner),
                        List.of(),
                        List.of(
                                new MenuAppearanceController.Pairing(
                                        products.get(0), products.get(2))));
        assertThatThrownBy(
                        () ->
                                appearance.save(
                                        b,
                                        new MenuAppearanceController.Input(
                                                2, outsideBranch, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("this branch menu");
        assertThatThrownBy(
                        () ->
                                appearance.save(
                                        b, new MenuAppearanceController.Input(1, config, false)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
