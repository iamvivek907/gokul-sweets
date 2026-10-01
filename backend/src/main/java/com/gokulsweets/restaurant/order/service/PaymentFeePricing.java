package com.gokulsweets.restaurant.order.service;
import com.gokulsweets.restaurant.order.entity.Order;
import java.math.BigDecimal;
import java.math.RoundingMode;
/** Rate is customer-visible and inclusive of the configured tax treatment, never fee-on-fee. */
public final class PaymentFeePricing {
 private PaymentFeePricing() {}
 private static BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
 public static BigDecimal fee(BigDecimal base,BigDecimal rate) {return base.max(BigDecimal.ZERO).multiply(rate==null?BigDecimal.ZERO:rate).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);}
 public static BigDecimal tax(BigDecimal fee,BigDecimal rate) {return fee.subtract(fee.multiply(new BigDecimal("100")).divide(new BigDecimal("100").add(rate==null?BigDecimal.ZERO:rate),2,RoundingMode.HALF_UP));}
 public static BigDecimal totalWithFee(Order order,BigDecimal base) {return base.add(fee(base,order.getPaymentFeeRate())).setScale(2,RoundingMode.HALF_UP);}
 public static void reprice(Order order) {
  BigDecimal base=zero(order.getSubtotal()).add(zero(order.getTaxAmount())).add(zero(order.getPriorityCharge())).add(zero(order.getConvenienceFee())).add(zero(order.getDeliveryFee())).subtract(order.getRebateDiscountAmount()==null?BigDecimal.ZERO:order.getRebateDiscountAmount()).max(BigDecimal.ZERO);
  order.setPaymentFee(fee(base,order.getPaymentFeeRate()));order.setPaymentFeeTax(tax(order.getPaymentFee(),order.getPaymentFeeTaxRate()));order.setTotalAmount(base.add(order.getPaymentFee()).setScale(2,RoundingMode.HALF_UP));
 }
}
