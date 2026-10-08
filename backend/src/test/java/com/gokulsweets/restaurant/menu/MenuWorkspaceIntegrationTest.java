package com.gokulsweets.restaurant.menu;
import com.gokulsweets.restaurant.menu.workspace.MenuWorkspaceService;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest @Transactional
class MenuWorkspaceIntegrationTest {
 @Autowired jakarta.persistence.EntityManager entityManager;
 @Autowired com.gokulsweets.restaurant.inventory.service.AdminInventoryService inventory;
 @Autowired java.time.Clock inventoryClock;
 @Autowired JdbcTemplate jdbc;@Autowired MenuWorkspaceService workspace;@Autowired MobileMenuOptionsService groups;
 @MockitoBean StaffAuthorizationService staff;
 @Autowired MenuServiceWindows windows;
 @BeforeEach void actor(){var actor=new StaffUser();actor.setUsername("workspace-test");when(staff.getCurrentStaff()).thenReturn(actor);}
 long branch(){return jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?, 'Workspace') RETURNING id",Long.class,UUID.randomUUID().toString());}
 long category(){return jdbc.queryForObject("INSERT INTO categories(code,name) VALUES (?, 'Sweets') RETURNING id",Long.class,UUID.randomUUID().toString());}
 MenuWorkspaceService.Details details(long c,String code,long version){return new MenuWorkspaceService.Details("Test product",code,c,"Fresh",BigDecimal.valueOf(120),ProductSaleMode.UNIT,null,null,null,version);}
 long product(long b,long c){return workspace.create(b,details(c,UUID.randomUUID().toString(),0),List.of(b));}
 long branchProduct(long b,long product){return jdbc.queryForObject("SELECT id FROM branch_products WHERE branch_id=? AND product_id=?",Long.class,b,product);}
 @Test void dietaryToggleDefaultsToVegPreservesBranchSettingsAndRejectsStaleEdits(){
  long b=branch(),c=category(),id=product(b,c),other=branch();
  jdbc.update("INSERT INTO branch_products(branch_id,product_id) VALUES (?,?)",other,id);
  var before=jdbc.queryForList("SELECT * FROM branch_products WHERE product_id=? ORDER BY id",id);
  assertThat(workspace.list(b,LocalDate.now(),"",null,"ALL",0,25).content().getFirst().get("vegetarian")).isEqualTo(true);
  workspace.editDietary(b,id,new MenuWorkspaceService.DietaryEdit(0L,false));
  assertThat(workspace.list(other,LocalDate.now(),"",null,"ALL",0,25).content().getFirst().get("vegetarian")).isEqualTo(false);
  assertThat(jdbc.queryForList("SELECT * FROM branch_products WHERE product_id=? ORDER BY id",id)).isEqualTo(before);
  assertThat(jdbc.queryForObject("SELECT workspace_version FROM products WHERE id=?",Long.class,id)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_workspace_audit WHERE product_id=? AND action='PRODUCT_DIETARY'",Integer.class,id)).isEqualTo(1);
  assertThatThrownBy(()->workspace.editDietary(b,id,new MenuWorkspaceService.DietaryEdit(0L,true))).isInstanceOf(ResponseStatusException.class);
  workspace.editDietary(b,id,new MenuWorkspaceService.DietaryEdit(1L,true));
  assertThat(jdbc.queryForObject("SELECT vegetarian FROM products WHERE id=?",Boolean.class,id)).isTrue();
 }
 @Test void staleJpaImageAndDetailsSavesPreserveNewDietaryClassification(){
  long b=branch(),c=category(),id=product(b,c);
  for(boolean target:List.of(false,true)){
   entityManager.clear();
   var stale=entityManager.find(Product.class,id);
   assertThat(stale.isVegetarian()).isEqualTo(!target);
   long version=jdbc.queryForObject("SELECT workspace_version FROM products WHERE id=?",Long.class,id);
   workspace.editDietary(b,id,new MenuWorkspaceService.DietaryEdit(version,target));
   // The image upload/import loaded this entity before the dietary change.
   assertThat(stale.isVegetarian()).isEqualTo(!target);
   String image="https://example.invalid/product-"+target+".jpg";
   stale.setImageUrl(image);
   stale.setName("Imported product "+target);
   stale.setBasePrice(BigDecimal.valueOf(target?140:130));
   entityManager.flush();
   assertThat(jdbc.queryForObject("SELECT vegetarian FROM products WHERE id=?",Boolean.class,id)).isEqualTo(target);
   assertThat(jdbc.queryForObject("SELECT image_url FROM products WHERE id=?",String.class,id)).isEqualTo(image);
   assertThat(jdbc.queryForObject("SELECT name FROM products WHERE id=?",String.class,id)).isEqualTo(stale.getName());
   assertThat(jdbc.queryForObject("SELECT base_price FROM products WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo(stale.getBasePrice());
  }
 }
 @Test void newJpaProductsStillDefaultToVeg(){
  var item=new Product();
  item.setCode(UUID.randomUUID().toString());
  item.setName("New imported product");
  item.setCategory(entityManager.getReference(com.gokulsweets.restaurant.category.Category.class,category()));
  item.setBasePrice(BigDecimal.TEN);
  entityManager.persist(item);
  entityManager.flush();
  assertThat(jdbc.queryForObject("SELECT vegetarian FROM products WHERE id=?",Boolean.class,item.getId())).isTrue();
 }
 @Test void dietaryChangesRequireAllAssignedBranchesAndMenuPermission(){
  long b=branch(),c=category(),id=product(b,c),other=branch();
  jdbc.update("INSERT INTO branch_products(branch_id,product_id) VALUES (?,?)",other,id);
  doThrow(new AccessDeniedException("Other branch")).when(staff).requireBranchAccess(other);
  assertThatThrownBy(()->workspace.editDietary(b,id,new MenuWorkspaceService.DietaryEdit(0L,false))).isInstanceOf(AccessDeniedException.class);
  doThrow(new AccessDeniedException("Menu permission")).when(staff).requirePermission(com.gokulsweets.restaurant.staff.PermissionName.MENU_MANAGE);
  assertThatThrownBy(()->workspace.editDietary(b,id,new MenuWorkspaceService.DietaryEdit(0L,false))).isInstanceOf(AccessDeniedException.class);
  assertThat(jdbc.queryForObject("SELECT vegetarian FROM products WHERE id=?",Boolean.class,id)).isTrue();
 }
 @Test void individualHoursPreserveOtherItemsAndAllNonTimingRules(){
  long b=branch(),c=category(),a=branchProduct(b,product(b,c)),d=branchProduct(b,product(b,c));
  var original=new MenuServiceWindows.Item(a,java.time.LocalTime.of(8,0),java.time.LocalTime.of(9,30),31,true,d);
  var other=new MenuServiceWindows.Item(d,java.time.LocalTime.of(11,0),java.time.LocalTime.of(21,30),127,false,null);
  windows.save(b,new MenuServiceWindows.Settings(true,0,List.of(original,other)));
  var saved=windows.saveHours(b,a,new MenuServiceWindows.HoursEdit(1,java.time.LocalTime.of(22,0),java.time.LocalTime.of(2,0)));
  assertThat(saved.revision()).isEqualTo(2);assertThat(saved.enabled()).isTrue();
  assertThat(saved.item().weekdays()).isEqualTo(31);assertThat(saved.item().soldOut()).isTrue();assertThat(saved.item().requiresBranchProductId()).isEqualTo(d);
  assertThat(windows.hours(b,d).item()).isEqualTo(other);
  var cleared=windows.saveHours(b,a,new MenuServiceWindows.HoursEdit(2,null,null));
  assertThat(cleared.item().startsAt()).isNull();assertThat(cleared.item().endsAt()).isNull();assertThat(cleared.item().soldOut()).isTrue();assertThat(cleared.item().requiresBranchProductId()).isEqualTo(d);assertThat(cleared.item().weekdays()).isEqualTo(31);
 }
 @Test void individualHoursRejectStaleDraftsAndNeverEnableTheBranchImplicitly(){
  long b=branch(),c=category(),a=branchProduct(b,product(b,c));
  var initial=windows.hours(b,a);assertThat(initial.enabled()).isFalse();assertThat(initial.revision()).isZero();assertThat(initial.item().weekdays()).isEqualTo(127);
  var edit=new MenuServiceWindows.HoursEdit(0,java.time.LocalTime.of(8,0),java.time.LocalTime.of(9,30));
  var saved=windows.saveHours(b,a,edit);assertThat(saved.enabled()).isFalse();assertThat(saved.revision()).isEqualTo(1);
  assertThatThrownBy(()->windows.saveHours(b,a,edit)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Reload");
  assertThat(windows.hours(b,a)).isEqualTo(saved);
  windows.save(b,new MenuServiceWindows.Settings(false,1,List.of(saved.item())));
  assertThatThrownBy(()->windows.saveHours(b,a,new MenuServiceWindows.HoursEdit(1,null,null))).isInstanceOf(ResponseStatusException.class);
 }
 @Test void individualHoursValidatePairedTimesAndBranchScope(){
  long b=branch(),c=category(),a=branchProduct(b,product(b,c)),other=branch();
  for(var edit:List.of(new MenuServiceWindows.HoursEdit(0,java.time.LocalTime.NOON,null),new MenuServiceWindows.HoursEdit(0,java.time.LocalTime.NOON,java.time.LocalTime.NOON))){
   assertThatThrownBy(()->windows.saveHours(b,a,edit)).isInstanceOf(IllegalArgumentException.class);
  }
  assertThatThrownBy(()->windows.hours(other,a)).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->windows.saveHours(other,a,new MenuServiceWindows.HoursEdit(0,null,null))).isInstanceOf(ResponseStatusException.class);
  assertThat(windows.hours(b,a).revision()).isZero();
  doThrow(new AccessDeniedException("Branch denied")).when(staff).requireBranchAccess(b);
  assertThatThrownBy(()->windows.saveHours(b,a,new MenuServiceWindows.HoursEdit(0,null,null))).isInstanceOf(AccessDeniedException.class);
 }
 @Test void boundedPagesSearchAndNewProductsStartUnavailable(){long b=branch(),c=category();for(int i=0;i<31;i++)product(b,c);var first=workspace.list(b,LocalDate.now(inventoryClock),"",null,"ALL",0,25);var next=workspace.list(b,LocalDate.now(inventoryClock),"",null,"ALL",1,25);assertThat(first.content()).hasSize(25);assertThat(next.content()).hasSize(6);assertThat(first.totalElements()).isEqualTo(31);assertThat(first.content()).allSatisfy(p->assertThat(p.get("available")).isEqualTo(false));assertThat(workspace.list(b,LocalDate.now(inventoryClock),"no match",null,"ALL",0,25).content()).isEmpty();assertThatThrownBy(()->workspace.list(b,LocalDate.now(inventoryClock),"",null,"ALL",0,1000)).isInstanceOf(IllegalArgumentException.class);}
 @Test void appearanceCategoriesAreBranchScopedButCreationCategoriesRemainGlobal(){long b=branch(),other=branch(),c=category(),foreign=category();product(b,c);product(other,foreign);var page=workspace.list(b,LocalDate.now(inventoryClock),"",null,"ALL",0,25);assertThat(page.categories()).extracting(MenuWorkspaceService.Option::id).contains(c,foreign);assertThat(page.branchCategories()).extracting(MenuWorkspaceService.Option::id).contains(c).doesNotContain(foreign);}
 @Test void countSkuPickerFiltersBeforePagination(){long b=branch(),c=category();for(int i=0;i<26;i++)product(b,c);workspace.create(b,new MenuWorkspaceService.Details("Loose sweet",UUID.randomUUID().toString(),c,"Fresh",BigDecimal.TEN,ProductSaleMode.WEIGHT,250,250,null,0L),List.of(b));var first=workspace.list(b,LocalDate.now(inventoryClock),"",null,"COUNT_SKU",0,25);var second=workspace.list(b,LocalDate.now(inventoryClock),"",null,"COUNT_SKU",1,25);assertThat(first.totalElements()).isEqualTo(26);assertThat(first.content()).hasSize(25);assertThat(second.content()).hasSize(1);assertThat(first.content()).allMatch(row->row.get("saleMode").equals("UNIT"));}
 @Test void allWritersAdvanceVersionAndStaleUpdateCannotOverwrite(){long b=branch(),c=category(),id=product(b,c);workspace.editBranch(b,id,new MenuWorkspaceService.BranchEdit(0L,true,null,false));assertThatThrownBy(()->workspace.editBranch(b,id,new MenuWorkspaceService.BranchEdit(0L,false,null,false))).isInstanceOf(ResponseStatusException.class);jdbc.update("UPDATE branch_products SET price_override=200 WHERE branch_id=? AND product_id=?",b,id);assertThatThrownBy(()->workspace.editBranch(b,id,new MenuWorkspaceService.BranchEdit(1L,false,null,false))).isInstanceOf(ResponseStatusException.class);assertThat(jdbc.queryForObject("SELECT available FROM branch_products WHERE branch_id=? AND product_id=?",Boolean.class,b,id)).isTrue();assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_workspace_audit WHERE branch_id=?",Integer.class,b)).isEqualTo(2);}
 @Test void sharedChangesRequireEveryAssignedBranch(){long b=branch(),other=branch(),c=category(),id=product(b,c);jdbc.update("INSERT INTO branch_products(branch_id,product_id) VALUES (?,?)",other,id);doThrow(new AccessDeniedException("Other branch")).when(staff).requireBranchAccess(other);assertThatThrownBy(()->workspace.editDetails(b,id,details(c,UUID.randomUUID().toString(),0))).isInstanceOf(AccessDeniedException.class);assertThat(jdbc.queryForObject("SELECT workspace_version FROM products WHERE id=?",Long.class,id)).isZero();}
 @Test void updatingOneGroupPreservesOtherGroupsAndUngroupKeepsProducts(){long b=branch(),c=category();long a=product(b,c),d=product(b,c),e=product(b,c),f=product(b,c);var first=new MobileMenuOptionsService.Group("one","First",List.of(new MobileMenuOptionsService.Choice(a,"Half"),new MobileMenuOptionsService.Choice(d,"Full")));var second=new MobileMenuOptionsService.Group("two","Second",List.of(new MobileMenuOptionsService.Choice(e,"Small"),new MobileMenuOptionsService.Choice(f,"Large")));assertThat(groups.saveOne(b,0,first,false)).isEqualTo(1);groups.saveOne(b,1,second,false);assertThat(groups.page(b,"",0).total()).isEqualTo(2);groups.saveOne(b,2,first,true);assertThat(groups.page(b,"",0).groups()).containsExactly(second);assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_products WHERE branch_id=?",Integer.class,b)).isEqualTo(4);assertThatThrownBy(()->groups.saveOne(b,2,first,false)).isInstanceOf(ResponseStatusException.class);}

 @Test void quantityAdjustmentsPreserveReadinessAndPausedStatuses(){
  for(String status:List.of("READY","DELAYED","UNAVAILABLE","CLOSED")){
   long b=branch(),c=category(),id=product(b,c);
   long bp=jdbc.queryForObject("SELECT id FROM branch_products WHERE branch_id=? AND product_id=?",Long.class,b,id);
   var date=LocalDate.now(inventoryClock);
   inventory.upsertPolicy(bp,new com.gokulsweets.restaurant.inventory.dto.AdminInventoryPolicyRequest(com.gokulsweets.restaurant.inventory.enums.InventoryControlMode.READY_STOCK,com.gokulsweets.restaurant.inventory.enums.InventoryUnit.PIECE,true,true,BigDecimal.ZERO,null,14,0,null));
   inventory.approveAllocation(bp,date,new com.gokulsweets.restaurant.inventory.dto.AdminAllocationApprovalRequest(BigDecimal.TEN,BigDecimal.ZERO,null,null,null,"Initial"),"test");
   entityManager.flush();entityManager.clear();
   jdbc.update("UPDATE inventory_daily_allocations SET status=?,ready_quantity=8 WHERE branch_product_id=? AND service_date=?",status,bp,date);
   long version=jdbc.queryForObject("SELECT version FROM inventory_daily_allocations WHERE branch_product_id=? AND service_date=?",Long.class,bp,date);
   workspace.stock(b,id,date,new MenuWorkspaceService.StockEdit(version,null,"Adjust quantity",new com.gokulsweets.restaurant.inventory.dto.AdminAllocationApprovalRequest(BigDecimal.valueOf(12),BigDecimal.ZERO,null,null,null,null),null,null));
   entityManager.flush();
   assertThat(jdbc.queryForObject("SELECT status FROM inventory_daily_allocations WHERE branch_product_id=? AND service_date=?",String.class,bp,date)).isEqualTo(status);
   var row=workspace.list(b,date,"",null,"ALL",0,25).content().getFirst();
   var allocation=(com.gokulsweets.restaurant.inventory.dto.InventoryAllocationResponse)row.get("allocation");
   assertThat(allocation.orderable()).isEqualTo(status.equals("READY"));
   assertThat(allocation.readyQuantity()).isEqualByComparingTo("8");
  }
 }
 @Test void stockAdjustmentsRetainHeldAndCommittedQuantities(){long b=branch(),c=category(),id=product(b,c);long bp=jdbc.queryForObject("SELECT id FROM branch_products WHERE branch_id=? AND product_id=?",Long.class,b,id);var date=LocalDate.now(inventoryClock);inventory.upsertPolicy(bp,new com.gokulsweets.restaurant.inventory.dto.AdminInventoryPolicyRequest(com.gokulsweets.restaurant.inventory.enums.InventoryControlMode.DAILY_PRODUCTION,com.gokulsweets.restaurant.inventory.enums.InventoryUnit.PIECE,true,false,BigDecimal.ZERO,null,14,0,null));inventory.approveAllocation(bp,date,new com.gokulsweets.restaurant.inventory.dto.AdminAllocationApprovalRequest(BigDecimal.TEN,BigDecimal.ZERO,null,null,null,"Initial"),"test");entityManager.flush();entityManager.clear();jdbc.update("UPDATE inventory_daily_allocations SET held_quantity=2,committed_quantity=1 WHERE branch_product_id=? AND service_date=?",bp,date);long version=jdbc.queryForObject("SELECT version FROM inventory_daily_allocations WHERE branch_product_id=? AND service_date=?",Long.class,bp,date);workspace.stock(b,id,date,new MenuWorkspaceService.StockEdit(version,null,"Fresh production",new com.gokulsweets.restaurant.inventory.dto.AdminAllocationApprovalRequest(BigDecimal.valueOf(12),BigDecimal.ZERO,null,null,null,null),null,null));assertThat(jdbc.queryForObject("SELECT held_quantity FROM inventory_daily_allocations WHERE branch_product_id=? AND service_date=?",BigDecimal.class,bp,date)).isEqualByComparingTo("2");assertThat(jdbc.queryForObject("SELECT committed_quantity FROM inventory_daily_allocations WHERE branch_product_id=? AND service_date=?",BigDecimal.class,bp,date)).isEqualByComparingTo("1");}
 @Test void availabilityPayloadMayOmitPriceReset() {
  var input=tools.jackson.databind.json.JsonMapper.builder().build().readValue("{\"version\":0,\"available\":false}",MenuWorkspaceService.BranchEdit.class);
  long b=branch(),c=category(),id=product(b,c);workspace.editBranch(b,id,input);
  assertThat(jdbc.queryForObject("SELECT available FROM branch_products WHERE branch_id=? AND product_id=?",Boolean.class,b,id)).isFalse();
 }
 @Test void deletionIsBranchScopedAndPreservesSharedProduct(){
  long b=branch(),other=branch(),c=category(),id=product(b,c);jdbc.update("INSERT INTO branch_products(branch_id,product_id) VALUES (?,?)",other,id);
  workspace.deleteBranchItem(b,id,0);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_products WHERE branch_id=? AND product_id=?",Integer.class,b,id)).isZero();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_products WHERE branch_id=? AND product_id=?",Integer.class,other,id)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE id=?",Integer.class,id)).isEqualTo(1);
 }
 @Test void staleOrUnauthorizedDeletionIsRejected(){
  long b=branch(),c=category(),id=product(b,c);
  assertThatThrownBy(()->workspace.deleteBranchItem(b,id,1)).isInstanceOf(ResponseStatusException.class);
  doThrow(new AccessDeniedException("Branch denied")).when(staff).requireBranchAccess(b);
  assertThatThrownBy(()->workspace.deleteBranchItem(b,id,0)).isInstanceOf(AccessDeniedException.class);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_products WHERE branch_id=? AND product_id=?",Integer.class,b,id)).isEqualTo(1);
 }
 @Test void deletionCannotEraseInventoryHistory(){
  long b=branch(),c=category(),id=product(b,c);long bp=jdbc.queryForObject("SELECT id FROM branch_products WHERE branch_id=? AND product_id=?",Long.class,b,id);
  inventory.upsertPolicy(bp,new com.gokulsweets.restaurant.inventory.dto.AdminInventoryPolicyRequest(com.gokulsweets.restaurant.inventory.enums.InventoryControlMode.DAILY_PRODUCTION,com.gokulsweets.restaurant.inventory.enums.InventoryUnit.PIECE,true,false,BigDecimal.ZERO,null,14,0,null));
  inventory.approveAllocation(bp,LocalDate.now(inventoryClock),new com.gokulsweets.restaurant.inventory.dto.AdminAllocationApprovalRequest(BigDecimal.TEN,BigDecimal.ZERO,null,null,null,"Initial"),"test");entityManager.flush();entityManager.clear();
  assertThatThrownBy(()->workspace.deleteBranchItem(b,id,0)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("history");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_daily_allocations WHERE branch_product_id=?",Integer.class,bp)).isEqualTo(1);
 }
 @Test void deletingOneOfTwoChoicesUngroupsSurvivorAndInvalidatesDraft(){
  long b=branch(),c=category(),a=product(b,c),d=product(b,c),e=product(b,c),f=product(b,c);
  var pair=new MobileMenuOptionsService.Group("pair","Pair",List.of(new MobileMenuOptionsService.Choice(a,"Half"),new MobileMenuOptionsService.Choice(d,"Full")));
  var other=new MobileMenuOptionsService.Group("other","Other",List.of(new MobileMenuOptionsService.Choice(e,"Small"),new MobileMenuOptionsService.Choice(f,"Large")));
  groups.saveOne(b,0,pair,false);groups.saveOne(b,1,other,false);
  workspace.deleteBranchItem(b,a,0);
  assertThat(groups.page(b,"",0).groups()).containsExactly(other);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM mobile_menu_choices WHERE branch_id=? AND product_id=?",Integer.class,b,d)).isZero();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_products WHERE branch_id=? AND product_id=?",Integer.class,b,d)).isEqualTo(1);
  assertThatThrownBy(()->groups.saveOne(b,2,other,false)).isInstanceOf(ResponseStatusException.class);
 }
 @Test void deletingOneOfThreeChoicesKeepsValidGroup(){
  long b=branch(),c=category(),a=product(b,c),d=product(b,c),e=product(b,c);
  groups.saveOne(b,0,new MobileMenuOptionsService.Group("three","Three",List.of(new MobileMenuOptionsService.Choice(a,"Small"),new MobileMenuOptionsService.Choice(d,"Medium"),new MobileMenuOptionsService.Choice(e,"Large"))),false);
  workspace.deleteBranchItem(b,a,0);
  assertThat(groups.page(b,"",0).groups().getFirst().choices()).extracting(MobileMenuOptionsService.Choice::productId).containsExactly(d,e);
 }
}
