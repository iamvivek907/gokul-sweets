package com.gokulsweets.restaurant.order.service;
import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.customer.identity.VerifiedOrderOwnership;
import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.rebate.RebateEligibilityService;
import com.gokulsweets.restaurant.rebate.dto.AvailableRebateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
@Service @RequiredArgsConstructor
public class MobileCheckoutPreview {
 private final VerifiedOrderOwnership ownership;
 private final CheckoutQuoteService quotes;
 private final RebateEligibilityService rebates;
 public record Preview(CheckoutQuoteService.Quote quote,List<AvailableRebateResponse> offers,List<AvailableRebateResponse> spendTargets) {}
 @Transactional
 public Preview preview(CreateOrderRequest request,String token){
  ownership.requireVerifiedIdentity(request.customerPhone(),token);
  var quote=quotes.preview(request,null);
  var order=new Order();var branch=new Branch();branch.setId(request.branchId());order.setBranch(branch);
  order.setCustomerPhone(request.customerPhone());order.setPickupType(request.pickupType());order.setFulfillmentType(FulfillmentType.PICKUP);
  order.setSubtotal(new BigDecimal(quote.subtotal()));order.setTaxAmount(new BigDecimal(quote.taxAmount()));order.setPriorityCharge(new BigDecimal(quote.priorityCharge()));
  order.setConvenienceFee(new BigDecimal(quote.convenienceFee()));order.setPaymentFeeRate(new BigDecimal(quote.paymentFeeRate()));
  var offers=rebates.previewDraft(order);
  return new Preview(quote,offers,rebates.previewSpendTargets(order,offers));
 }
}
