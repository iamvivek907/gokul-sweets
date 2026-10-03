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
 private final com.gokulsweets.restaurant.loyalty.LoyaltyService loyalty;
 public record Preview(CheckoutQuoteService.Quote quote,List<AvailableRebateResponse> offers,List<AvailableRebateResponse> spendTargets,BigDecimal paymentFee,BigDecimal paymentFeeTax,com.gokulsweets.restaurant.loyalty.LoyaltyService.Wallet rewards,BigDecimal rewardDiscount,BigDecimal totalBeforeOffer,AvailableRebateResponse selectedOffer) {}
 @Transactional
 public Preview preview(CreateOrderRequest request,String token){
  ownership.requireVerifiedIdentity(request.customerPhone(),token);
  var quote=quotes.preview(request,null);
  var order=new Order();var branch=new Branch();branch.setId(request.branchId());order.setBranch(branch);
  order.setCustomerPhone(request.customerPhone());order.setPickupType(request.pickupType());order.setFulfillmentType(FulfillmentType.PICKUP);
  order.setSubtotal(new BigDecimal(quote.subtotal()));order.setTaxAmount(new BigDecimal(quote.taxAmount()));order.setPriorityCharge(new BigDecimal(quote.priorityCharge()));
  order.setConvenienceFee(new BigDecimal(quote.convenienceFee()));order.setPaymentFeeRate(new BigDecimal(quote.paymentFeeRate()));
  com.gokulsweets.restaurant.loyalty.LoyaltyService.Wallet wallet=null;
  if(loyalty.enabled()){
   var subject=ownership.verifiedSubject(request.customerPhone(),token);
   var excluded=loyalty.excludedProducts();
   BigDecimal eligible=BigDecimal.ZERO;
   for(var line:quote.items())if(!excluded.contains(line.productId()))eligible=eligible.add(new BigDecimal(line.total()).subtract(new BigDecimal(line.taxAmount())));
   wallet=loyalty.wallet(subject.environment(),subject.id(),eligible);
   var reward=loyalty.preview(subject.environment(),subject.id(),eligible,request.rewardCode());
   order.setLoyaltyEnrolled(true);order.setLoyaltyDiscount(reward==null?BigDecimal.ZERO:reward.discount());
  }else if(request.rewardCode()!=null&&!request.rewardCode().isBlank())throw new IllegalStateException("Rewards are currently unavailable.");
  var offers=rebates.previewDraft(order);
  var best=offers.stream().min(java.util.Comparator.comparing(AvailableRebateResponse::payableAfterRebate));
  if(request.offerCode()!=null&&!request.offerCode().isBlank()){
   var selected=rebates.findEligibleDraftRebate(order,request.offerCode()).orElseThrow(OfferIneligibleException::new);
   best=java.util.Optional.of(selected);
   if(offers.stream().noneMatch(offer->offer.code().equals(selected.code())))offers=java.util.stream.Stream.concat(offers.stream(),java.util.stream.Stream.of(selected)).toList();
  }
  BigDecimal fee=new BigDecimal(quote.paymentFee()),feeTax=new BigDecimal(quote.paymentFeeTax());
  BigDecimal noOfferBase=order.getSubtotal().add(order.getTaxAmount()).add(order.getPriorityCharge()).subtract(order.getLoyaltyDiscount()).add(order.getConvenienceFee());
  fee=PaymentFeePricing.fee(noOfferBase,order.getPaymentFeeRate());feeTax=PaymentFeePricing.tax(fee,new BigDecimal(quote.paymentFeeTaxRate()));
  if(best.isPresent()){
   BigDecimal base=order.getSubtotal().add(order.getTaxAmount()).add(order.getPriorityCharge()).subtract(order.getLoyaltyDiscount()).subtract(best.get().rebateAmount()).max(BigDecimal.ZERO).add(order.getConvenienceFee());
   fee=PaymentFeePricing.fee(base,order.getPaymentFeeRate());
   feeTax=PaymentFeePricing.tax(fee,new BigDecimal(quote.paymentFeeTaxRate()));
  }
  BigDecimal base=order.getSubtotal().add(order.getTaxAmount()).add(order.getPriorityCharge()).add(order.getConvenienceFee()).subtract(order.getLoyaltyDiscount());
  BigDecimal noOffer=PaymentFeePricing.totalWithFee(order,base);
  return new Preview(quote,offers,rebates.previewSpendTargets(order,offers),fee,feeTax,wallet,order.getLoyaltyDiscount(),noOffer,best.orElse(null));
 }
}
