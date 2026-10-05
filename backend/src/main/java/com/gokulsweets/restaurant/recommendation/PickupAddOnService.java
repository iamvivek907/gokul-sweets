package com.gokulsweets.restaurant.recommendation;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.dto.CustomerInventoryCheckRequest;
import com.gokulsweets.restaurant.inventory.service.CustomerInventoryAvailabilityService;
import com.gokulsweets.restaurant.menu.MenuService;
import com.gokulsweets.restaurant.menu.dto.MenuProductResponse;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.*;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.OrderValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

/** Bounded public projection of branch-local anonymous basket aggregates; never returns private reporting metrics. */
@Service @RequiredArgsConstructor
public class PickupAddOnService {
 private final JdbcTemplate jdbc;
 private final MenuService menu;
 private final CustomerInventoryAvailabilityService inventory;
 private final OrderRepository orders;
 private final OrderValidationService validation;
 private final EnhancementProperties flags;
 private final Clock inventoryClock;
 private final com.gokulsweets.restaurant.order.service.CartAvailabilityService slotAvailability;
 public record Suggestion(MenuProductResponse product,Integer weightGrams,BigDecimal portionPrice,BigDecimal portionTotal,String reason,boolean slotVerified) {}
 public record Availability(boolean orderable) {}
 record Pair(long seed,long candidate,double confidence,long count) {}

 private Order ownedPending(long branch,CustomerInventoryCheckRequest request,String number) {
  if(number==null)return null;
  var order=orders.findByOrderNumber(number).orElseThrow(()->new IllegalArgumentException("Order not found."));
  if(!order.getBranch().getId().equals(branch) || order.getFulfillmentType()!=FulfillmentType.PICKUP || order.getPickupType()!=PickupType.NORMAL || order.getOrderStatus()!=OrderStatus.PENDING_PAYMENT || !order.getPickupSlot().getSlotDate().equals(request.serviceDate()))
   throw new IllegalStateException("Review your current normal pickup before adding items.");
  return order;
 }
 private boolean fits(long branch,CustomerInventoryCheckRequest request,Order order) {
  if(order==null)return inventory.checkRequestedDate(branch,request).orderable();
  try {
   validation.validateExistingReservationUpdate(order,order.getPickupSlot().getId(),order.getPickupType(),request.items().stream().map(i->new CreateOrderItemRequest(i.productId(),i.quantity(),i.weightGrams())).toList());
   return true;
  } catch(IllegalArgumentException|IllegalStateException unavailable){return false;}
 }
 @Transactional(readOnly=true)
 public Availability check(long branch,CustomerInventoryCheckRequest request,String number) {
  return new Availability(fits(branch,request,ownedPending(branch,request,number)));
 }
 @Transactional(readOnly=true)
 public List<Suggestion> recommend(long branch,CustomerInventoryCheckRequest request,String number) {
  return recommend(branch,request,number,null,null);
 }
 @Transactional(readOnly=true)
 public List<Suggestion> recommend(long branch,CustomerInventoryCheckRequest request,String number,Long pickupSlotId,PickupType pickupType) {
  if((pickupSlotId==null)!=(pickupType==null))throw new IllegalArgumentException("Choose both pickup slot and type.");
  if(pickupSlotId!=null&&!flags.isSmartAvailability())throw new IllegalArgumentException("Slot availability is not enabled.");
  if(!flags.isPickupAddOns())return List.of();
  LocalDate today=LocalDate.now(inventoryClock.withZone(ZoneId.of("Asia/Kolkata")));
  if(request.serviceDate()==null || request.serviceDate().isBefore(today) || request.serviceDate().isAfter(today.plusDays(flags.getFutureOrderingDays())))throw new IllegalArgumentException("Choose an available pickup date in India.");
  if(request.items()==null || request.items().isEmpty() || request.items().size()>30)throw new IllegalArgumentException("Choose between one and thirty cart items.");
  Order order=ownedPending(branch,request,number);
  if(order!=null&&pickupSlotId!=null&&(!order.getPickupSlot().getId().equals(pickupSlotId)||order.getPickupType()!=pickupType))
   throw new IllegalArgumentException("Review the reserved pickup slot.");
  var products=new HashMap<Long,MenuProductResponse>();
  menu.getMenu(branch).forEach(c->c.products().forEach(p->{if(p.available() && p.price().signum()>0)products.put(p.id(),p);}));
  boolean collectTax=Boolean.TRUE.equals(jdbc.queryForObject("SELECT enabled FROM tax_collection_settings WHERE id=1",Boolean.class));
  var tax=new HashMap<Long,BigDecimal>();
  jdbc.query("SELECT p.id,t.cgst_rate+t.sgst_rate FROM products p JOIN tax_categories t ON t.id=p.tax_category_id AND t.active WHERE p.active",(org.springframework.jdbc.core.RowCallbackHandler)rs->tax.put(rs.getLong(1),collectTax?rs.getBigDecimal(2):BigDecimal.ZERO));
  products.keySet().removeIf(id->!tax.containsKey(id));
  var cart=new HashSet<Long>();
  for(var item:request.items()) {
   if(item==null || item.productId()==null || !cart.add(item.productId()) || !products.containsKey(item.productId()))throw new IllegalArgumentException("Choose distinct products available at this branch.");
   var p=products.get(item.productId());
   if(p.saleMode()==ProductSaleMode.WEIGHT) {int min=p.minimumWeightGrams()==null?250:p.minimumWeightGrams(),step=p.weightStepGrams()==null?50:p.weightStepGrams();if(item.weightGrams()==null || item.weightGrams()<min || item.weightGrams()>100000000 || step<=0 || (item.weightGrams()-min)%step!=0)throw new IllegalArgumentException("Choose a valid weight.");}
   else if(item.quantity()==null || item.quantity()<1 || item.quantity()>100000)throw new IllegalArgumentException("Choose a valid quantity.");
  }
  var args=new ArrayList<Object>();args.add(branch);args.add(today.minusDays(29));args.add(today);args.add(branch);args.add(today.minusDays(29));args.add(today);args.addAll(cart);args.addAll(cart);
  String marks=String.join(",",Collections.nCopies(cart.size(),"?"));
  var rows=jdbc.query("""
   WITH pairs AS (SELECT product_a_id a,product_b_id b,SUM(pair_order_count) n FROM analytics_product_pair_daily WHERE branch_id=? AND business_date BETWEEN ? AND ? GROUP BY product_a_id,product_b_id),
   products AS (SELECT product_id,SUM(order_count) n FROM analytics_product_daily WHERE branch_id=? AND business_date BETWEEN ? AND ? GROUP BY product_id)
   SELECT pairs.a,pairs.b,pairs.n,pa.n an,pb.n bn FROM pairs JOIN products pa ON pa.product_id=pairs.a JOIN products pb ON pb.product_id=pairs.b
   WHERE pairs.n>=3 AND (pairs.a IN (
   """+marks+") OR pairs.b IN ("+marks+"))",(rs,n)->new long[]{rs.getLong("a"),rs.getLong("b"),rs.getLong("n"),rs.getLong("an"),rs.getLong("bn")},args.toArray());
  var best=new HashMap<Long,Pair>();
  for(var row:rows) {boolean a=cart.contains(row[0]),b=cart.contains(row[1]);if(a==b)continue;long seed=a?row[0]:row[1],candidate=a?row[1]:row[0],seedOrders=a?row[3]:row[4];if(seedOrders<5 || row[2]>seedOrders || !products.containsKey(candidate))continue;double confidence=(double)row[2]/seedOrders;if(confidence<.1)continue;var pair=new Pair(seed,candidate,confidence,row[2]);best.merge(candidate,pair,(x,y)->x.confidence()>=y.confidence()?x:y);}
  var ranked=new LinkedHashMap<Long,String>();
  best.values().stream().sorted(Comparator.comparingDouble(Pair::confidence).reversed().thenComparing(Comparator.comparingLong(Pair::count).reversed()).thenComparingLong(Pair::candidate)).forEach(p->ranked.put(p.candidate(),"Often ordered with "+products.get(p.seed()).name()));
  jdbc.query("SELECT product_id,SUM(order_count) n FROM analytics_product_daily WHERE branch_id=? AND business_date BETWEEN ? AND ? GROUP BY product_id HAVING SUM(order_count)>=3 ORDER BY n DESC,product_id ASC",(rs,n)->rs.getLong(1),branch,today.minusDays(29),today).stream().filter(id->products.containsKey(id)&&!cart.contains(id)).forEach(id->ranked.putIfAbsent(id,"A branch favourite"));
  var candidates=ranked.keySet().stream().limit(8).toList();
  if(candidates.isEmpty())return List.of();
  var combined=new ArrayList<>(request.items());
  for(var id:candidates){var p=products.get(id);combined.add(new CustomerInventoryCheckRequest.Item(id,p.saleMode()==ProductSaleMode.WEIGHT?null:1,p.saleMode()==ProductSaleMode.WEIGHT?(p.minimumWeightGrams()==null?250:p.minimumWeightGrams()):null));}
  var allowed=new HashSet<Long>();
  if(order==null&&pickupSlotId!=null){
   var checked=slotAvailability.check(branch,request.serviceDate(),1,combined.stream().map(i->new CreateOrderItemRequest(i.productId(),i.quantity(),i.weightGrams())).toList());
   var slot=checked.dates().stream().filter(d->d.date().equals(request.serviceDate())).flatMap(d->d.slots().stream()).filter(v->v.slot().id().equals(pickupSlotId)).findFirst().orElse(null);
   boolean capacity=slot!=null&&slot.slot().active()&&slot.slot().slotDate().atTime(slot.slot().startTime()).isAfter(LocalDateTime.now(inventoryClock.withZone(ZoneId.of("Asia/Kolkata"))))&&(pickupType==PickupType.PRIORITY?slot.slot().priorityEnabled()&&slot.slot().priorityRemainingCapacity()>0:slot.slot().remainingCapacity()>0);
   if(capacity&&!"PICKUP_WINDOW".equals(slot.code())&&!slot.issues().stream().anyMatch(i->cart.contains(i.productId())&&!i.available()))
    candidates.stream().filter(id->slot.issues().stream().noneMatch(i->i.productId().equals(id)&&!i.available())).forEach(allowed::add);
  }else if(order==null){
   var checked=inventory.checkRequestedDate(branch,new CustomerInventoryCheckRequest(request.serviceDate(),combined));
   if(!checked.enforcementEnabled())allowed.addAll(candidates);
   else if(cart.stream().allMatch(id->checked.items().stream().anyMatch(i->i.productId().equals(id)&&i.orderable())))
    checked.items().stream().filter(i->i.orderable()&&candidates.contains(i.productId())).map(i->i.productId()).forEach(allowed::add);
  }else{
   // Reserved stock belongs to this order: retain reservation-aware validation.
   for(var id:candidates){var single=new ArrayList<>(request.items());single.add(combined.stream().filter(i->i.productId().equals(id)).findFirst().orElseThrow());if(fits(branch,new CustomerInventoryCheckRequest(request.serviceDate(),single),order))allowed.add(id);}
  }
  var result=new ArrayList<Suggestion>();
  for(var id:candidates){if(!allowed.contains(id))continue;if(result.size()==3)break;var p=products.get(id);Integer grams=p.saleMode()==ProductSaleMode.WEIGHT?(p.minimumWeightGrams()==null?250:p.minimumWeightGrams()):null;
   BigDecimal base=(grams==null?p.price():p.price().multiply(BigDecimal.valueOf(grams)).divide(BigDecimal.valueOf(1000))).setScale(2,RoundingMode.HALF_UP);
   BigDecimal total=base.add(base.multiply(tax.get(id)).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP));
   result.add(new Suggestion(p,grams,base,total,ranked.get(id),pickupSlotId!=null));
  }
  return List.copyOf(result);
 }
}
