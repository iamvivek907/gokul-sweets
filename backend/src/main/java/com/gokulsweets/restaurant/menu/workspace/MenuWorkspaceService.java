package com.gokulsweets.restaurant.menu.workspace;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.product.*;
import com.gokulsweets.restaurant.inventory.dto.*;
import com.gokulsweets.restaurant.inventory.repository.*;
import com.gokulsweets.restaurant.inventory.service.InventoryAvailabilityService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MenuWorkspaceService {
 private final JdbcTemplate jdbc;
 private final StaffAuthorizationService staff;
 private final ProductService products;
 private final com.gokulsweets.restaurant.inventory.service.AdminInventoryService inventory;
 private final BranchInventoryPolicyRepository policies;
 private final InventoryDailyAllocationRepository allocations;
 private final InventoryAvailabilityService availability;
 public record Details(@NotBlank @Size(max=150) String name,@NotBlank @Size(max=100) String code,
  @NotNull @Positive Long categoryId,@Size(max=500) String description,
  @NotNull @DecimalMin("0.01") BigDecimal basePrice,@NotNull ProductSaleMode saleMode,
  @Min(1) Integer minimumWeightGrams,@Min(1) Integer weightStepGrams,Long taxCategoryId,
  @NotNull @Min(0) Long version) {}
 public record BranchEdit(@NotNull @Min(0) Long version,Boolean available,
  @DecimalMin("0.01") BigDecimal priceOverride,boolean clearPriceOverride) {}
 public record Option(Long id,String name) {}
 public record Page(List<Map<String,Object>> content,long totalElements,int page,int totalPages,
  List<Option> categories,List<Option> taxes) {}
 private void authorize(long branch,boolean inventory){staff.requirePermission(PermissionName.MENU_MANAGE);staff.requireBranchAccess(branch);if(inventory)staff.requirePermission(PermissionName.INVENTORY_VIEW);}
 private void shared(long branch,long product){authorize(branch,false);var bs=jdbc.queryForList("SELECT branch_id FROM branch_products WHERE product_id=? ORDER BY branch_id",Long.class,product);if(!bs.contains(branch))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not assigned to this branch.");bs.forEach(staff::requireBranchAccess);}
 private static final String BALANCE="GREATEST((CASE WHEN pol.ready_stock_required THEN LEAST(al.ready_quantity,al.approved_quantity) ELSE al.approved_quantity END)-al.safety_buffer_quantity-al.held_quantity-al.committed_quantity-al.wasted_quantity,0)";
 @Transactional(readOnly=true)
 public Page list(long branch,LocalDate date,String search,Long category,String filter,int page,int size){
  authorize(branch,true);if(page<0||page>100000||size<1||size>50)throw new IllegalArgumentException("Use a page size between 1 and 50.");
  var args=new HashMap<String,Object>();args.put("branch",branch);args.put("date",date);args.put("limit",size);args.put("offset",(long)page*size);
  String from=" FROM branch_products bp JOIN products p ON p.id=bp.product_id JOIN categories c ON c.id=p.category_id LEFT JOIN branch_inventory_policies pol ON pol.branch_product_id=bp.id LEFT JOIN inventory_daily_allocations al ON al.branch_product_id=bp.id AND al.service_date=:date WHERE bp.branch_id=:branch";
  if(search!=null&&!search.isBlank()){if(search.length()>100)throw new IllegalArgumentException("Search is too long.");args.put("search","%"+search.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");from+=" AND (lower(p.name) LIKE :search OR lower(p.code) LIKE :search)";}
  if(category!=null){args.put("category",category);from+=" AND p.category_id=:category";}
  from+=switch(filter){case "ALL"->"";case "COUNT_SKU"->" AND p.sale_mode='UNIT'";case "UNAVAILABLE"->" AND (NOT bp.available OR NOT p.active OR NOT c.active)";case "MISSING_IMAGE"->" AND (p.image_url IS NULL OR p.image_url='')";case "OVERRIDE"->" AND bp.price_override IS NOT NULL";case "GROUPED"->" AND EXISTS(SELECT 1 FROM mobile_menu_choices mc WHERE mc.branch_id=bp.branch_id AND mc.product_id=p.id)";case "LOW_STOCK"->" AND al.id IS NOT NULL AND "+BALANCE+"<=al.safety_buffer_quantity";default->throw new IllegalArgumentException("Unknown filter.");};
  var named=new NamedParameterJdbcTemplate(jdbc);
  long total=Objects.requireNonNull(named.queryForObject("SELECT count(*)"+from,args,Long.class));
  var rows=named.queryForList("SELECT bp.id AS \"branchProductId\",p.id AS \"productId\",p.code,p.name,p.description,p.category_id AS \"categoryId\",c.name AS \"categoryName\",p.base_price AS \"basePrice\",bp.price_override AS \"priceOverride\",COALESCE(bp.price_override,p.base_price) AS \"effectivePrice\",bp.available,p.active,p.sale_mode AS \"saleMode\",p.minimum_weight_grams AS \"minimumWeightGrams\",p.weight_step_grams AS \"weightStepGrams\",p.tax_category_id AS \"taxCategoryId\",p.image_url AS \"imageUrl\",p.workspace_version AS \"productVersion\",bp.workspace_version AS \"branchVersion\""+from+" ORDER BY c.display_order,bp.display_order,p.name,p.id LIMIT :limit OFFSET :offset",args);
  if(!rows.isEmpty()){
   var ids=rows.stream().map(r->((Number)r.get("branchProductId")).longValue()).toList();
   var ps=new HashMap<Long,com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy>();policies.findByBranchProductIdIn(ids).forEach(p->ps.put(p.getBranchProduct().getId(),p));
   var als=new HashMap<Long,com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation>();allocations.findByBranchProductIdInAndServiceDate(ids,date).forEach(a->als.put(a.getBranchProduct().getId(),a));
   for(var row:rows){long id=((Number)row.get("branchProductId")).longValue();var p=ps.get(id);var a=als.get(id);row.put("policyVersion",p==null?null:p.getVersion());row.put("allocationVersion",a==null?null:a.getVersion());row.put("policy",p==null?null:InventoryPolicyResponse.from(p));row.put("allocation",p==null||a==null?null:InventoryAllocationResponse.from(a,availability.calculate(a,p)));}
  }
  var cats=jdbc.query("SELECT id,name FROM categories WHERE active=true ORDER BY name LIMIT 200",(rs,n)->new Option(rs.getLong(1),rs.getString(2)));
  var taxes=jdbc.query("SELECT id,name FROM tax_categories WHERE active=true ORDER BY name LIMIT 200",(rs,n)->new Option(rs.getLong(1),rs.getString(2)));
  return new Page(rows,total,page,(int)((total+size-1)/size),cats,taxes);
 }
 private Map<String,Object> product(long id,boolean lock){var rows=jdbc.queryForList("SELECT * FROM products WHERE id=?"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found.");return rows.getFirst();}
 private void audit(long branch,Long product,String action,Object before,Object after){jdbc.update("INSERT INTO menu_workspace_audit(actor,branch_id,product_id,action,before_state,after_state) VALUES (?,?,?,?,?,?)",staff.getCurrentStaff().getUsername(),branch,product,action,String.valueOf(before),String.valueOf(after));}
 private void validate(Details d){if(d.saleMode()==ProductSaleMode.WEIGHT&&(d.minimumWeightGrams()==null||d.weightStepGrams()==null))throw new IllegalArgumentException("Weight products need minimum and step quantities.");if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM categories WHERE id=? AND active=true)",Boolean.class,d.categoryId())))throw new IllegalArgumentException("Choose an active category.");if(d.taxCategoryId()!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM tax_categories WHERE id=? AND active=true)",Boolean.class,d.taxCategoryId())))throw new IllegalArgumentException("Choose an active tax category.");}
 @Transactional
 public void editDetails(long branch,long id,Details d){shared(branch,id);validate(d);var old=product(id,true);if(((Number)old.get("workspace_version")).longValue()!=d.version())conflict();
  // Sale-unit changes invalidate existing stock units and orders; use a new SKU instead.
  if(!Objects.equals(((Number)old.get("category_id")).longValue(),d.categoryId())&&jdbc.queryForObject("SELECT count(*) FROM mobile_menu_choices WHERE product_id=?",Long.class,id)>0)throw new IllegalArgumentException("Ungroup this product before changing its category.");
  if(!old.get("sale_mode").equals(d.saleMode().name()))throw new IllegalArgumentException("An existing SKU's sale unit cannot change. Create a new product instead.");
  jdbc.update("UPDATE products SET name=?,code=?,category_id=?,description=?,base_price=?,minimum_weight_grams=?,weight_step_grams=?,tax_category_id=?,updated_at=now() WHERE id=?",d.name().trim(),d.code().trim(),d.categoryId(),d.description(),d.basePrice(),d.saleMode()==ProductSaleMode.WEIGHT?d.minimumWeightGrams():null,d.saleMode()==ProductSaleMode.WEIGHT?d.weightStepGrams():null,d.taxCategoryId(),id);audit(branch,id,"PRODUCT_DETAILS",old,d);
 }
 @Transactional
 public long create(long branch,Details d,List<Long> branches){authorize(branch,false);validate(d);if(branches==null||branches.isEmpty()||branches.size()>50||!branches.contains(branch))throw new IllegalArgumentException("Assign the selected branch and up to 50 permitted branches.");for(long b:new TreeSet<>(branches)){staff.requireBranchAccess(b);if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM branches WHERE id=? AND active=true)",Boolean.class,b)))throw new IllegalArgumentException("Branch not active.");}
  Long id=jdbc.queryForObject("INSERT INTO products(code,category_id,name,description,base_price,sale_mode,minimum_weight_grams,weight_step_grams,active,tax_category_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,true,?,now(),now()) RETURNING id",Long.class,d.code().trim(),d.categoryId(),d.name().trim(),d.description(),d.basePrice(),d.saleMode().name(),d.saleMode()==ProductSaleMode.WEIGHT?d.minimumWeightGrams():null,d.saleMode()==ProductSaleMode.WEIGHT?d.weightStepGrams():null,d.taxCategoryId());
  for(long b:new TreeSet<>(branches))jdbc.update("INSERT INTO branch_products(branch_id,product_id,available,display_order,created_at,updated_at) VALUES (?,?,false,0,now(),now())",b,id);audit(branch,id,"CREATE_PRODUCT",null,d);return id;
 }
 @Transactional
 public void editBranch(long branch,long id,BranchEdit d){authorize(branch,false);if(d.clearPriceOverride()&&d.priceOverride()!=null)throw new IllegalArgumentException("Reset and override cannot be combined.");var rows=jdbc.queryForList("SELECT * FROM branch_products WHERE branch_id=? AND product_id=? FOR UPDATE",branch,id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not assigned to branch.");var old=rows.getFirst();if(((Number)old.get("workspace_version")).longValue()!=d.version())conflict();jdbc.update("UPDATE branch_products SET available=COALESCE(?,available),price_override=CASE WHEN ? THEN NULL ELSE COALESCE(?,price_override) END,updated_at=now() WHERE branch_id=? AND product_id=?",d.available(),d.clearPriceOverride(),d.priceOverride(),branch,id);audit(branch,id,"BRANCH_UPDATE",old,d);}
 @Transactional
 public String image(long branch,long id,long version,MultipartFile image,boolean remove){shared(branch,id);var old=product(id,true);if(((Number)old.get("workspace_version")).longValue()!=version)conflict();var saved=remove?products.removeImage(id):products.uploadImage(id,image);audit(branch,id,"PRODUCT_IMAGE",old.get("image_url"),saved.imageUrl());return saved.imageUrl();}
 private void conflict(){throw new ResponseStatusException(HttpStatus.CONFLICT,"This item changed since you opened it. Close and reload the item before saving; your entries have been retained.");}


 public record StockEdit(@Min(0) Long version,@Min(0) Long policyVersion,@NotBlank @Size(max=500) String reason,@NotNull @jakarta.validation.Valid AdminAllocationApprovalRequest allocation,@jakarta.validation.Valid AdminInventoryPolicyRequest policy,@jakarta.validation.Valid AdminReadinessUpdateRequest readiness) {}
 @Transactional
 public void stock(long branch,long product,LocalDate date,StockEdit edit){
  authorize(branch,true);staff.requirePermission(PermissionName.INVENTORY_MANAGE);
  var ids=jdbc.queryForList("SELECT id FROM branch_products WHERE branch_id=? AND product_id=? FOR UPDATE",Long.class,branch,product);
  if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not in branch.");
  long bp=ids.getFirst();var old=jdbc.queryForList("SELECT * FROM inventory_daily_allocations WHERE branch_product_id=? AND service_date=? FOR UPDATE",bp,date);
  if(old.isEmpty()?edit.version()!=null:edit.version()==null||((Number)old.getFirst().get("version")).longValue()!=edit.version())conflict();
  if(edit.policy()!=null){var existing=jdbc.queryForList("SELECT version FROM branch_inventory_policies WHERE branch_product_id=? FOR UPDATE",Long.class,bp);if(existing.isEmpty()?edit.policyVersion()!=null:edit.policyVersion()==null||!existing.getFirst().equals(edit.policyVersion()))conflict();inventory.upsertPolicy(bp,edit.policy());}
  var i=edit.allocation();var saved=inventory.approveAllocation(bp,date,new AdminAllocationApprovalRequest(i.approvedQuantity(),i.safetyBufferQuantity(),i.forecastQuantity(),i.forecastConfidence(),i.expectedReadyAt(),edit.reason()),staff.getCurrentStaff().getUsername());
  if(edit.readiness()!=null){var ready=edit.readiness();saved=inventory.updateReadiness(bp,date,new AdminReadinessUpdateRequest(ready.status(),ready.readyQuantity(),ready.expectedReadyAt(),edit.reason()),staff.getCurrentStaff().getUsername());}
  audit(branch,product,"INVENTORY_ADJUSTMENT",old.isEmpty()?null:old.getFirst(),saved);
 }
}
