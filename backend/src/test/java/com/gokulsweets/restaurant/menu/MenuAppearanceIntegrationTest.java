package com.gokulsweets.restaurant.menu;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest @Transactional
class MenuAppearanceIntegrationTest {
 @Autowired MenuAppearanceController appearance;@Autowired JdbcTemplate jdbc;@MockitoBean StaffAuthorizationService staff;
 @Test void draftIsPrivateUntilPublishAndStaleVersionsAreRejected(){var actor=new StaffUser();actor.setUsername("test");when(staff.getCurrentStaff()).thenReturn(actor);long b=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?, 'Appearance') RETURNING id",Long.class,UUID.randomUUID().toString());var banner=new MenuAppearanceController.Banner("sweet","Sweet moments","मिठास","Browse sweets","","",null,true,0,null,null,null,null,null,new MenuAppearanceController.Frame(50,50,100,"COVER"));var config=new MenuAppearanceController.Config(List.of(banner),List.of());appearance.save(b,new MenuAppearanceController.Input(0,config,false));assertThat(appearance.live(b).banners()).isEmpty();appearance.save(b,new MenuAppearanceController.Input(1,config,true));assertThat(appearance.live(b).banners()).containsExactly(banner);assertThatThrownBy(()->appearance.save(b,new MenuAppearanceController.Input(1,config,false))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
}
