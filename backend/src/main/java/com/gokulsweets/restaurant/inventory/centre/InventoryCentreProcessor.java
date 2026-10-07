package com.gokulsweets.restaurant.inventory.centre;

import com.gokulsweets.restaurant.inventory.centre.InventoryCentreJobs.*;
import com.gokulsweets.restaurant.inventory.dto.*;
import com.gokulsweets.restaurant.inventory.enums.*;
import com.gokulsweets.restaurant.inventory.repository.*;
import com.gokulsweets.restaurant.menu.workspace.MenuWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class InventoryCentreProcessor {
 private final JdbcTemplate jdbc;
 private final InventoryCentreJobs jobs;
 private final MenuWorkspaceService workspace;
 private final BranchInventoryPolicyRepository policies;
 private final InventoryDailyAllocationRepository allocations;
 private final Clock inventoryClock;
 @Transactional
 public void apply(long branch,Work work){
  jobs.authorize(branch,true);var today=LocalDate.now(inventoryClock);var o=work.options();var e=work.entry();
  jdbc.queryForObject("SELECT id FROM branches WHERE id=? AND active=true FOR UPDATE",Long.class,branch);
  jdbc.update("INSERT INTO mobile_menu_config(branch_id) VALUES (?) ON CONFLICT DO NOTHING",branch);
  long groupVersion=jdbc.queryForObject("SELECT version FROM mobile_menu_config WHERE branch_id=? FOR UPDATE",Long.class,branch);
  if(groupVersion!=work.groupVersion())throw new IllegalArgumentException("Grouping changed. Reload the plan before retrying.");
  var rows=jdbc.queryForList("SELECT id,workspace_version FROM branch_products WHERE branch_id=? AND product_id=? FOR UPDATE",branch,e.productId());
  if(rows.isEmpty())throw new IllegalArgumentException("Item is no longer assigned to this branch.");
  var row=rows.getFirst();long bp=((Number)row.get("id")).longValue();
  if(((Number)row.get("workspace_version")).longValue()!=e.branchVersion())throw new IllegalArgumentException("Branch item changed. Reload before retrying.");
  jdbc.queryForList("SELECT id FROM branch_inventory_policies WHERE branch_product_id=? FOR UPDATE",bp);
  var policy=policies.findByBranchProductId(bp).orElse(null);
  if(policy==null?e.policyVersion()!=null:e.policyVersion()==null||!policy.getVersion().equals(e.policyVersion()))throw new IllegalArgumentException("Inventory policy changed. Reload before retrying.");
  var timing=jobs.timing(bp);
  if(o.applyHours()&&!Objects.equals(timing,work.timing()))throw new IllegalArgumentException("Item service hours changed. Reload before retrying.");
  if(o.applyInventory()){
  boolean prepared=o.method().equals("READY_STOCK");
  if(policy!=null&&!o.applyPolicy()&&(!policy.getControlMode().name().equals(o.method())||policy.isReadyStockRequired()!=prepared||!policy.isOnlineEnabled()))throw new IllegalArgumentException("Policy differs from this plan. Enable Apply selling method or use the individual editor.");
  if(prepared&&o.fromDate().equals(today)&&e.readyQuantity()==null)throw new IllegalArgumentException("Confirm the physically prepared quantity for today.");
  if(e.readyQuantity()!=null&&e.readyQuantity().compareTo(e.quantity())>0)throw new IllegalArgumentException("Prepared stock exceeds the online allocation.");
  String sale=jdbc.queryForObject("SELECT sale_mode FROM products WHERE id=? FOR SHARE",String.class,e.productId());
  if(!Objects.equals(sale,e.saleMode()))throw new IllegalArgumentException("Item selling unit changed. Reload before retrying.");
  var unit=InventoryUnit.valueOf("WEIGHT".equals(sale)?"GRAM":"PIECE");
  if(policy!=null&&policy.getInventoryUnit()!=unit)throw new IllegalArgumentException("Existing inventory units differ. Use the individual editor; bulk jobs cannot convert stock units.");
  int horizon=(int)Math.max(14,java.time.temporal.ChronoUnit.DAYS.between(today,o.throughDate()));
  var replacement=policy==null||o.applyPolicy()?new AdminInventoryPolicyRequest(InventoryControlMode.valueOf(o.method()),unit,true,prepared,policy==null?BigDecimal.ZERO:policy.getDefaultSafetyBuffer(),policy==null?null:policy.getMaximumDailyAllocation(),policy==null?horizon:policy.getBookingHorizonDays(),policy==null?0:policy.getProductionLeadMinutes(),policy==null?null:policy.getShelfLifeMinutes()):null;
  for(int index=0;index<work.dates().size();index++){
   var dv=work.dates().get(index);if(dv.date().isBefore(today))throw new IllegalArgumentException("Job crossed into a past service date. Review dates and submit a fresh plan.");
   var allocation=allocations.findByBranchProductIdAndServiceDate(bp,dv.date()).orElse(null);
   if(allocation!=null&&List.of("DELAYED","UNAVAILABLE","CLOSED").contains(allocation.getStatus().name()))throw new IllegalArgumentException("A date is paused or closed; review it individually before reopening.");
   var buffer=allocation!=null?allocation.getSafetyBufferQuantity():policy==null?BigDecimal.ZERO:policy.getDefaultSafetyBuffer();
   var ready=prepared&&dv.date().equals(today)?new AdminReadinessUpdateRequest(InventoryAllocationStatus.READY,e.readyQuantity(),null,o.reason()):null;
   workspace.stock(branch,e.productId(),dv.date(),new MenuWorkspaceService.StockEdit(dv.version(),index==0?e.policyVersion():null,o.reason(),new AdminAllocationApprovalRequest(e.quantity(),buffer,allocation==null?null:allocation.getForecastQuantity(),allocation==null?null:allocation.getForecastConfidence(),allocation==null?null:allocation.getExpectedReadyAt(),o.reason()),index==0?replacement:null,ready));
  }
  }
  if(o.applyHours()){
   jdbc.update("INSERT INTO menu_service_items(branch_product_id,starts_at,ends_at,weekdays,sold_out,requires_branch_product_id) VALUES (?,?,?,?,?,?) ON CONFLICT(branch_product_id) DO UPDATE SET starts_at=EXCLUDED.starts_at,ends_at=EXCLUDED.ends_at,weekdays=EXCLUDED.weekdays",bp,o.opens(),o.closes(),o.weekdays(),timing!=null&&timing.soldOut(),timing==null?null:timing.dependency());
   jdbc.update("INSERT INTO menu_service_policies(branch_id,enabled,revision) VALUES (?,?,1) ON CONFLICT(branch_id) DO UPDATE SET enabled=menu_service_policies.enabled OR EXCLUDED.enabled,revision=menu_service_policies.revision+1",branch,o.enableHours());
  }
  if(o.applyInventory()&&!today.equals(LocalDate.now(inventoryClock)))throw new IllegalArgumentException("The job crossed midnight in IST. Review dates and verify stock in a fresh plan.");
  if(o.openPurchases())workspace.editBranch(branch,e.productId(),new MenuWorkspaceService.BranchEdit(e.branchVersion(),true,null,false));
  if(o.applyHours())jdbc.update("INSERT INTO menu_workspace_audit(actor,branch_id,product_id,action,before_state,after_state) VALUES (?,?,?,?,?,?)",jobs.actorName(),branch,e.productId(),"CENTRE_SERVICE_HOURS",String.valueOf(timing),String.valueOf(o));
 }
}
