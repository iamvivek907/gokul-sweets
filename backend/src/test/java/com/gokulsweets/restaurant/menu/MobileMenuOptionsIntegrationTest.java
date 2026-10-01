package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @Transactional
class MobileMenuOptionsIntegrationTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired MobileMenuOptionsService options;
 @MockitoBean StaffAuthorizationService staff;
 String key(){return UUID.randomUUID().toString();}
 long branch(){return jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?, 'Portions') RETURNING id",Long.class,key());}
 long category(){return jdbc.queryForObject("INSERT INTO categories(code,name) VALUES (?, 'Meals') RETURNING id",Long.class,key());}
 long product(long branch,long category,String mode){
  long tax=jdbc.queryForObject("INSERT INTO tax_categories(code,name) VALUES (?, 'Tax') RETURNING id",Long.class,key());
  long id=jdbc.queryForObject("INSERT INTO products(code,category_id,tax_category_id,name,base_price,sale_mode) VALUES (?,?,?,'Portion',100,?) RETURNING id",Long.class,key(),category,tax,mode);
  jdbc.update("INSERT INTO branch_products(branch_id,product_id,available) VALUES (?,?,true)",branch,id);return id;
 }
 MobileMenuOptionsService.Input input(long version,long half,long full){return new MobileMenuOptionsService.Input(version,List.of(new MobileMenuOptionsService.Group("meal","Meal",List.of(new MobileMenuOptionsService.Choice(half,"Half"),new MobileMenuOptionsService.Choice(full,"Full")))));}
 @Test void groupsKeepTheirSkuPricesAndRejectStaleWrites(){
  long b=branch(),c=category(),h=product(b,c,"UNIT"),f=product(b,c,"UNIT");
  jdbc.update("UPDATE branch_products SET price_override=180 WHERE branch_id=? AND product_id=?",b,f);
  var saved=options.save(b,input(0,h,f));assertThat(saved.version()).isEqualTo(1);assertThat(options.publicRead(b)).isEqualTo(saved);
  assertThatThrownBy(()->options.save(b,input(0,f,h))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
  assertThat(options.adminRead(b)).isEqualTo(saved);
  assertThat(jdbc.queryForObject("SELECT price_override FROM branch_products WHERE branch_id=? AND product_id=?",Integer.class,b,f)).isEqualTo(180);
  verify(staff,atLeastOnce()).requirePermission(PermissionName.MENU_MANAGE);verify(staff,atLeastOnce()).requireBranchAccess(b);
 }
 @Test void cannotGroupAnotherBranchWeightedOrDifferentCategoryProducts(){
  long b=branch(),c=category(),h=product(b,c,"UNIT");
  assertThatThrownBy(()->options.save(b,input(0,h,product(branch(),c,"UNIT")))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->options.save(b,input(0,h,product(b,c,"WEIGHT")))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->options.save(b,input(0,h,product(b,category(),"UNIT")))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->options.save(b,input(0,h,h))).isInstanceOf(IllegalArgumentException.class);
  assertThat(options.publicRead(b).groups()).isEmpty();
 }
 @Test void unauthorizedStaffCannotReadOrWriteAnotherBranch(){
  long b=branch();doThrow(new AccessDeniedException("Other branch")).when(staff).requireBranchAccess(b);
  assertThatThrownBy(()->options.adminRead(b)).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->options.save(b,new MobileMenuOptionsService.Input(0,List.of()))).isInstanceOf(AccessDeniedException.class);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM mobile_menu_config WHERE branch_id=?",Long.class,b)).isZero();
 }
}
