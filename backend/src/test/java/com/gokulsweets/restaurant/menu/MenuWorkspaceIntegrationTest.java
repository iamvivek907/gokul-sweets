package com.gokulsweets.restaurant.menu;
import com.gokulsweets.restaurant.menu.workspace.MenuWorkspaceService;
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
 @Autowired JdbcTemplate jdbc;@Autowired MenuWorkspaceService workspace;@Autowired MobileMenuOptionsService groups;
 @MockitoBean StaffAuthorizationService staff;
 @BeforeEach void actor(){var actor=new StaffUser();actor.setUsername("workspace-test");when(staff.getCurrentStaff()).thenReturn(actor);}
 long branch(){return jdbc.queryForObject("INSERT INTO branches(code,name) VALUES (?, 'Workspace') RETURNING id",Long.class,UUID.randomUUID().toString());}
 long category(){return jdbc.queryForObject("INSERT INTO categories(code,name) VALUES (?, 'Sweets') RETURNING id",Long.class,UUID.randomUUID().toString());}
 MenuWorkspaceService.Details details(long c,String code,long version){return new MenuWorkspaceService.Details("Test product",code,c,"Fresh",BigDecimal.valueOf(120),ProductSaleMode.UNIT,null,null,null,version);}
 long product(long b,long c){return workspace.create(b,details(c,UUID.randomUUID().toString(),0),List.of(b));}
 @Test void boundedPagesSearchAndNewProductsStartUnavailable(){long b=branch(),c=category();for(int i=0;i<31;i++)product(b,c);var first=workspace.list(b,LocalDate.now(),"",null,"ALL",0,25);var next=workspace.list(b,LocalDate.now(),"",null,"ALL",1,25);assertThat(first.content()).hasSize(25);assertThat(next.content()).hasSize(6);assertThat(first.totalElements()).isEqualTo(31);assertThat(first.content()).allSatisfy(p->assertThat(p.get("available")).isEqualTo(false));assertThat(workspace.list(b,LocalDate.now(),"no match",null,"ALL",0,25).content()).isEmpty();assertThatThrownBy(()->workspace.list(b,LocalDate.now(),"",null,"ALL",0,1000)).isInstanceOf(IllegalArgumentException.class);}
 @Test void allWritersAdvanceVersionAndStaleUpdateCannotOverwrite(){long b=branch(),c=category(),id=product(b,c);workspace.editBranch(b,id,new MenuWorkspaceService.BranchEdit(0L,true,null,false));assertThatThrownBy(()->workspace.editBranch(b,id,new MenuWorkspaceService.BranchEdit(0L,false,null,false))).isInstanceOf(ResponseStatusException.class);jdbc.update("UPDATE branch_products SET price_override=200 WHERE branch_id=? AND product_id=?",b,id);assertThatThrownBy(()->workspace.editBranch(b,id,new MenuWorkspaceService.BranchEdit(1L,false,null,false))).isInstanceOf(ResponseStatusException.class);assertThat(jdbc.queryForObject("SELECT available FROM branch_products WHERE branch_id=? AND product_id=?",Boolean.class,b,id)).isTrue();assertThat(jdbc.queryForObject("SELECT count(*) FROM menu_workspace_audit WHERE branch_id=?",Integer.class,b)).isEqualTo(2);}
 @Test void sharedChangesRequireEveryAssignedBranch(){long b=branch(),other=branch(),c=category(),id=product(b,c);jdbc.update("INSERT INTO branch_products(branch_id,product_id) VALUES (?,?)",other,id);doThrow(new AccessDeniedException("Other branch")).when(staff).requireBranchAccess(other);assertThatThrownBy(()->workspace.editDetails(b,id,details(c,UUID.randomUUID().toString(),0))).isInstanceOf(AccessDeniedException.class);assertThat(jdbc.queryForObject("SELECT workspace_version FROM products WHERE id=?",Long.class,id)).isZero();}
 @Test void updatingOneGroupPreservesOtherGroupsAndUngroupKeepsProducts(){long b=branch(),c=category();long a=product(b,c),d=product(b,c),e=product(b,c),f=product(b,c);var first=new MobileMenuOptionsService.Group("one","First",List.of(new MobileMenuOptionsService.Choice(a,"Half"),new MobileMenuOptionsService.Choice(d,"Full")));var second=new MobileMenuOptionsService.Group("two","Second",List.of(new MobileMenuOptionsService.Choice(e,"Small"),new MobileMenuOptionsService.Choice(f,"Large")));assertThat(groups.saveOne(b,0,first,false)).isEqualTo(1);groups.saveOne(b,1,second,false);assertThat(groups.page(b,"",0).total()).isEqualTo(2);groups.saveOne(b,2,first,true);assertThat(groups.page(b,"",0).groups()).containsExactly(second);assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_products WHERE branch_id=?",Integer.class,b)).isEqualTo(4);assertThatThrownBy(()->groups.saveOne(b,2,first,false)).isInstanceOf(ResponseStatusException.class);}

 @Test void stockAdjustmentsRetainHeldAndCommittedQuantities(){long b=branch(),c=category(),id=product(b,c);long bp=jdbc.queryForObject("SELECT id FROM branch_products WHERE branch_id=? AND product_id=?",Long.class,b,id);var date=LocalDate.now();inventory.upsertPolicy(bp,new com.gokulsweets.restaurant.inventory.dto.AdminInventoryPolicyRequest(com.gokulsweets.restaurant.inventory.enums.InventoryControlMode.DAILY_PRODUCTION,com.gokulsweets.restaurant.inventory.enums.InventoryUnit.PIECE,true,false,BigDecimal.ZERO,null,14,0,null));inventory.approveAllocation(bp,date,new com.gokulsweets.restaurant.inventory.dto.AdminAllocationApprovalRequest(BigDecimal.TEN,BigDecimal.ZERO,null,null,null,"Initial"),"test");entityManager.flush();entityManager.clear();jdbc.update("UPDATE inventory_daily_allocations SET held_quantity=2,committed_quantity=1 WHERE branch_product_id=? AND service_date=?",bp,date);long version=jdbc.queryForObject("SELECT version FROM inventory_daily_allocations WHERE branch_product_id=? AND service_date=?",Long.class,bp,date);workspace.stock(b,id,date,new MenuWorkspaceService.StockEdit(version,null,"Fresh production",new com.gokulsweets.restaurant.inventory.dto.AdminAllocationApprovalRequest(BigDecimal.valueOf(12),BigDecimal.ZERO,null,null,null,null),null,null));assertThat(jdbc.queryForObject("SELECT held_quantity FROM inventory_daily_allocations WHERE branch_product_id=? AND service_date=?",BigDecimal.class,bp,date)).isEqualByComparingTo("2");assertThat(jdbc.queryForObject("SELECT committed_quantity FROM inventory_daily_allocations WHERE branch_product_id=? AND service_date=?",BigDecimal.class,bp,date)).isEqualByComparingTo("1");}
}
