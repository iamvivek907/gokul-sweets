package com.gokulsweets.restaurant.order.correction;

import com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox;
import com.gokulsweets.restaurant.inventory.service.OrderInventoryLifecycleService;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.*;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OrderCorrectionService {
  private final OrderRepository orders;
  private final PaymentRepository payments;
  private final PickupSlotRepository slots;
  private final OrderInventoryLifecycleService inventory;
  private final com.gokulsweets.restaurant.inventory.service.OrderInventoryReservationService
      reservations;
  private final com.gokulsweets.restaurant.order.service.OrderValidationService validation;
  private final com.gokulsweets.restaurant.order.service.OrderCalculationService calculation;
  private final com.gokulsweets.restaurant.loyalty.LoyaltyService loyalty;
  private final com.gokulsweets.restaurant.delivery.DeliveryRiderHoldService delivery;
  private final StaffAuthorizationService staff;
  private final CustomerNotificationInbox notifications;
  private final com.gokulsweets.restaurant.staff.notification.StaffOrderAlerts staffAlerts;
  private final JdbcTemplate jdbc;
  private final Clock inventoryClock;
  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  private static final Set<OrderStatus> CANCEL =
      Set.of(
          OrderStatus.CONFIRMED,
          OrderStatus.PREPARING,
          OrderStatus.READY_FOR_PICKUP,
          OrderStatus.READY_FOR_DELIVERY,
          OrderStatus.PICKUP_WINDOW_EXPIRED);

  public record Summary(
      String orderNumber,
      String branchName,
      Instant serverTime,
      Instant cancellationDeadline,
      boolean canCancel,
      boolean canTransfer,
      BigDecimal refundAmount,
      BigDecimal retainedCharges,
      String refundStatus,
      String explanation) {}

  public record Cancellation(
      UUID requestKey,
      String reason,
      BigDecimal acceptedRefundAmount,
      BigDecimal acceptedRetainedCharges) {}

  public record Transfer(UUID requestKey, long targetBranchId, long targetSlotId, String reason) {}

  private Order locked(String number) {
    return orders
        .findForUpdate(number.trim().toUpperCase(Locale.ROOT))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public Summary preview(String number, boolean manager) {
    var order =
        orders
            .findDetailedByOrderNumber(number.toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (manager) authorize(order, false);
    return summary(order, manager);
  }

  private void authorize(Order order, boolean changing) {
    staff.requireBranchAccess(order.getBranch().getId());
    staff.requirePermission(changing ? PermissionName.ORDER_CANCEL : PermissionName.ORDER_VIEW);
    if (changing) staff.requirePermission(PermissionName.REFUND_CREATE);
  }

  private BigDecimal retained(Order order) {
    return money(order.getPriorityCharge())
        .add(money(order.getDeliveryFee()))
        .add(money(order.getConvenienceFee()))
        .add(money(order.getPaymentFee()));
  }

  private static BigDecimal money(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value.setScale(2, java.math.RoundingMode.HALF_UP);
  }

  private List<Payment> captured(Order order) {
    return payments.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
        .filter(p -> p.getPaymentStatus() == PaymentStatus.PAID)
        .toList();
  }

  private Summary summary(Order order, boolean manager) {
    var list = payments.findByOrderIdOrderByCreatedAtDesc(order.getId());
    var paid = list.stream().filter(p -> p.getPaymentStatus() == PaymentStatus.PAID).toList();
    Payment payment =
        paid.size() == 1
            ? paid.getFirst()
            : list.stream().filter(p -> p.getRefundReferenceId() != null).findFirst().orElse(null);
    Instant deadline =
        payment != null && payment.getPaidAt() != null
            ? payment.getPaidAt().atZone(IST).toInstant().plusSeconds(600)
            : null;
    BigDecimal fees = retained(order),
        food = money(order.getTotalAmount()).subtract(fees).max(BigDecimal.ZERO);
    boolean cancel =
        CANCEL.contains(order.getOrderStatus())
            && paid.size() == 1
            && (manager || deadline != null && inventoryClock.instant().isBefore(deadline));
    String state =
        payment != null && payment.getRefundReferenceId() != null
            ? payment.getPaymentStatus().name()
            : order.getOrderStatus() == OrderStatus.CANCELLED
                    && payment != null
                    && payment.getRefundAmount() != null
                    && payment.getRefundAmount().signum() == 0
                ? "NO_REFUND_DUE"
                : "NOT_REQUESTED";
    return new Summary(
        order.getOrderNumber(),
        order.getBranch().getName(),
        inventoryClock.instant(),
        deadline,
        cancel,
        manager
            && order.getOrderStatus() == OrderStatus.CONFIRMED
            && order.getFulfillmentType() == FulfillmentType.PICKUP
            && paid.size() == 1,
        food,
        fees,
        state,
        cancel
            ? "The food amount is refundable. Priority, delivery, convenience and payment"
                  + " processing charges, including their taxes, are not refunded."
            : "Customer cancellation is available for 10 minutes after payment confirmation,"
                  + " including during preparation. Collected or dispatched orders cannot be"
                  + " cancelled here. Contact the branch for help.");
  }

  @Transactional
  public Summary cancel(String number, Cancellation input, boolean manager) {
    var order = locked(number);
    if (manager) authorize(order, true);
    validate(input == null ? null : input.requestKey(), input == null ? null : input.reason());
    var previous =
        jdbc.queryForList(
            "SELECT kind,reason,refund_amount,retained_charges FROM order_corrections WHERE"
                + " order_id=? AND request_key=?",
            order.getId(),
            input.requestKey());
    if (!previous.isEmpty()) {
      if (!"CANCEL".equals(previous.getFirst().get("kind"))
          || !input.reason().trim().equals(previous.getFirst().get("reason"))
          || input.acceptedRefundAmount() == null
          || input.acceptedRetainedCharges() == null
          || input
                  .acceptedRefundAmount()
                  .compareTo((BigDecimal) previous.getFirst().get("refund_amount"))
              != 0
          || input
                  .acceptedRetainedCharges()
                  .compareTo((BigDecimal) previous.getFirst().get("retained_charges"))
              != 0) throw conflict("This request key was already used for another action.");
      return summary(order, manager);
    }
    if (order.getOrderStatus() == OrderStatus.CANCELLED
        && Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM order_corrections WHERE order_id=? AND kind='CANCEL')",
                Boolean.class,
                order.getId()))) return summary(order, manager);
    var preview = summary(order, manager);
    if (!preview.canCancel())
      throw conflict(
          "The cancellation window has ended or this order cannot be cancelled. Contact the"
              + " branch.");
    if (input.acceptedRefundAmount() == null
        || input.acceptedRetainedCharges() == null
        || input.acceptedRefundAmount().compareTo(preview.refundAmount()) != 0
        || input.acceptedRetainedCharges().compareTo(preview.retainedCharges()) != 0)
      throw conflict("Review the current refund and retained charges before cancelling.");
    var payment = captured(order).getFirst();
    jdbc.queryForList("SELECT id FROM payments WHERE id=? FOR UPDATE", Long.class, payment.getId());
    if (payment.getAmount().compareTo(order.getTotalAmount()) != 0)
      throw conflict("Payment reconciliation is required before cancelling.");
    String actor = manager ? "staff:" + staff.getCurrentStaff().getId() : "customer";
    inventory.cancelOrderInventory(order.getOrderNumber(), input.reason(), actor);
    release(order);
    order.setOrderStatus(OrderStatus.CANCELLED);
    orders.saveAndFlush(order);
    payment.setRefundAmount(preview.refundAmount());
    if (preview.refundAmount().signum() > 0) {
      // Save once with the cancellation: database ids can overlap across DEV/PROD.
      payment.setRefundReferenceId("GKC-" + UUID.randomUUID());
      payment.setPaymentStatus(PaymentStatus.REFUND_PENDING);
      payment.setRefundFailureReason(null);
      payments.saveAndFlush(payment);
      notifications.paymentChanged(payment.getId());
    }
    payments.saveAndFlush(payment);
    jdbc.update(
        "INSERT INTO"
            + " order_corrections(order_id,request_key,kind,source_branch_id,actor,reason,refund_amount,retained_charges)"
            + " VALUES (?,?,'CANCEL',?,?,?,?,?)",
        order.getId(),
        input.requestKey(),
        order.getBranch().getId(),
        actor,
        input.reason().trim(),
        preview.refundAmount(),
        preview.retainedCharges());
    loyalty.reconcile(order.getId());
    notifications.orderReady(order.getId());
    notifications.correction(
        order.getId(),
        input.requestKey(),
        "Order cancelled",
        preview.refundAmount().signum() > 0
            ? "Food refund ₹"
                + preview.refundAmount()
                + " requested. Additional charges ₹"
                + preview.retainedCharges()
                + " are retained. Provider confirmation is pending."
            : "Order cancelled. No food payment remains to refund; additional charges are"
                  + " retained.");
    return summary(order, manager);
  }

  private void release(Order order) {
    if (order.getFulfillmentType() == FulfillmentType.DELIVERY) {
      if (!delivery.release(order.getDeliveryHoldKey()))
        throw conflict("The delivery reservation requires staff review.");
    } else {
      int changed =
          order.getPickupType() == PickupType.NORMAL
              ? slots.releaseNormalCapacity(order.getPickupSlot().getId())
              : slots.releasePriorityCapacity(order.getPickupSlot().getId());
      if (changed != 1) throw conflict("Pickup capacity requires staff review.");
    }
  }

  private void validate(UUID key, String reason) {
    if (key == null || reason == null || reason.isBlank() || reason.trim().length() > 500)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Choose a reason and a request key.");
  }

  private ResponseStatusException conflict(String text) {
    return new ResponseStatusException(HttpStatus.CONFLICT, text);
  }

  public record Reschedule(UUID requestKey, long targetSlotId, String reason) {}

  @Transactional
  public Summary reschedule(String number, Reschedule input) {
    var order=locked(number);
    if(!Set.of("OWNER_ADMIN","MANAGER").contains(staff.getCurrentStaff().getRole().getName()))
      throw new org.springframework.security.access.AccessDeniedException("Only admin or manager can change pickup timing.");
    if(input==null)throw new IllegalArgumentException("Choose a pickup slot and reason.");
    return movePickup(number,new Transfer(input.requestKey(),order.getBranch().getId(),input.targetSlotId(),input.reason()),true);
  }

  @Transactional
  public Summary transfer(String number, Transfer input) {
    return movePickup(number,input,false);
  }

  private Summary movePickup(String number, Transfer input, boolean reschedule) {
    String kind=reschedule?"RESCHEDULE":"TRANSFER";
    var order = locked(number);
    staff.requirePermission(PermissionName.ORDER_CANCEL);
    staff.requirePermission(PermissionName.ORDER_VIEW);
    staff.requireBranchAccess(order.getBranch().getId());
    validate(input == null ? null : input.requestKey(), input == null ? null : input.reason());
    staff.requireBranchAccess(input.targetBranchId());
    var replay =
        jdbc.queryForList(
            "SELECT kind,target_branch_id,target_slot_id,reason FROM order_corrections WHERE"
                + " order_id=? AND request_key=?",
            order.getId(),
            input.requestKey());
    if (!replay.isEmpty()) {
      var row = replay.getFirst();
      if (!kind.equals(row.get("kind"))
          || ((Number) row.get("target_branch_id")).longValue() != input.targetBranchId()
          || ((Number) row.get("target_slot_id")).longValue() != input.targetSlotId()
          || !input.reason().trim().equals(row.get("reason")))
        throw conflict("This request key was already used for another action.");
      return summary(order, true);
    }
    if (!summary(order, true).canTransfer() || (!reschedule && order.getBranch().getId() == input.targetBranchId())
        || Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM kot WHERE order_id=?)",Boolean.class,order.getId())))
      throw conflict(
          "Pickup can change only before preparation starts and before a KOT exists.");
    if(reschedule && order.getPickupSlot().getId()==input.targetSlotId())
      throw conflict("Choose a different pickup slot.");
    // Lock both parent policy rows in stable order to serialize closures and pricing changes.
    jdbc.queryForList(
        "SELECT id FROM branches WHERE id IN (?,?) ORDER BY id FOR SHARE",
        Long.class,
        order.getBranch().getId(),
        input.targetBranchId());
    if (order.getRebate() != null
        && order.getRebate().getBranch() != null
        && order.getRebate().getBranch().getId() != input.targetBranchId())
      throw conflict(
          "This offer belongs to the original branch. Cancel/refund and place a new order"
              + " instead.");
    var items =
        order.getItems().stream()
            .map(
                i ->
                    new com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest(
                        i.getProduct().getId(), i.getQuantity(), i.getWeightGrams()))
            .toList();
    jdbc.queryForList(
        "SELECT id FROM branch_products WHERE branch_id=? AND product_id IN (SELECT product_id FROM"
            + " order_items WHERE order_id=?) ORDER BY id FOR SHARE",
        Long.class,
        input.targetBranchId(),
        order.getId());
    jdbc.queryForList(
        "SELECT id FROM products WHERE id IN (SELECT product_id FROM order_items WHERE order_id=?)"
            + " ORDER BY id FOR SHARE",
        Long.class,
        order.getId());
    jdbc.queryForList(
        "SELECT id FROM tax_categories WHERE id IN (SELECT p.tax_category_id FROM products p JOIN"
            + " order_items i ON i.product_id=p.id WHERE i.order_id=?) ORDER BY id FOR SHARE",
        Long.class,
        order.getId());
    var target =
        validation.validate(
            new com.gokulsweets.restaurant.order.dto.CreateOrderRequest(
                input.targetBranchId(),
                input.targetSlotId(),
                order.getCustomerName(),
                order.getCustomerPhoneNormalized(),
                order.getPickupType(),
                items));
    var priced = calculation.calculate(target);
    if (priced.items().size() != order.getItems().size()
        || priced.priorityCharge().compareTo(money(order.getPriorityCharge())) != 0
        || priced.convenienceFee().compareTo(money(order.getConvenienceFee())) != 0
        || priced.convenienceFeeTax().compareTo(money(order.getConvenienceFeeTax())) != 0
        || priced.convenienceFeeTaxRate().compareTo(money(order.getConvenienceFeeTaxRate())) != 0
        || priced.paymentFeeRate().compareTo(money(order.getPaymentFeeRate())) != 0
        || priced.paymentFeeTaxRate().compareTo(money(order.getPaymentFeeTaxRate())) != 0)
      throw conflict("Branch charges differ. Cancel/refund and place a new order instead.");
    for (var line : priced.items()) {
      var old =
          order.getItems().stream()
              .filter(i -> i.getProduct().getId().equals(line.product().getId()))
              .findFirst()
              .orElseThrow();
      if (old.getUnitPrice().compareTo(line.unitPrice()) != 0
          || old.getTaxRate().compareTo(line.taxRate()) != 0
          || old.getTaxAmount().compareTo(line.taxAmount()) != 0
          || old.getLineTotal().compareTo(line.lineTotal()) != 0)
        throw conflict("Item prices or taxes differ. Cancel/refund and place a new order instead.");
    }
    var oldBranch = order.getBranch().getId();
    String actor = "staff:" + staff.getCurrentStaff().getId();
    int reserved =
        order.getPickupType() == PickupType.NORMAL
            ? slots.reserveNormalCapacity(input.targetSlotId())
            : slots.reservePriorityCapacity(input.targetSlotId());
    if (reserved != 1) throw conflict("The receiving pickup slot is full.");
    boolean hadStock =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM inventory_reservations WHERE order_number=? AND"
                    + " status='CONFIRMED')",
                Boolean.class,
                order.getOrderNumber()));
    inventory.cancelOrderInventory(order.getOrderNumber(), input.reason(), actor);
    release(order);
    order.setBranch(target.branch());
    order.setPickupSlot(target.pickupSlot());
    reservations.reserveTransferredOrder(order, target, hadStock);
    inventory.confirmTransferredOrderInventory(order.getOrderNumber());
    orders.saveAndFlush(order);
    jdbc.update(
        "INSERT INTO"
            + " order_corrections(order_id,request_key,kind,source_branch_id,target_branch_id,target_slot_id,actor,reason)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        order.getId(),
        input.requestKey(),
        kind,
        oldBranch,
        input.targetBranchId(),
        input.targetSlotId(),
        actor,
        input.reason().trim());
    staffAlerts.pickupTransferred(order, input.requestKey());
    notifications.correction(
        order.getId(),
        input.requestKey(),
        reschedule?"Pickup time changed":"Pickup branch changed",
        "Collect this order only at "
            + order.getBranch().getName()
            + ". Your order number and pickup code stay the same. Check the updated pickup time in"
            + " your order.");
    return summary(order, true);
  }
}
