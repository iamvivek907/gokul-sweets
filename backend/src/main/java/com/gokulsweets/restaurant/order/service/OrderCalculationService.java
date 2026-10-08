package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.service.model.CalculatedOrderItem;
import com.gokulsweets.restaurant.order.service.model.OrderCalculationResult;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderData;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import com.gokulsweets.restaurant.tax.TaxCategory;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Coordinates order calculation operations. */
@Service
@Slf4j
public class OrderCalculationService {

    private com.gokulsweets.restaurant.tax.TaxCollectionSettings taxSettings;

    /**
     * Updates tax settings.
     *
     * @param settings the settings
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setTaxSettings(com.gokulsweets.restaurant.tax.TaxCollectionSettings settings) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCalculationService.class,
                        "setTaxSettings(com.gokulsweets.restaurant.tax.TaxCollectionSettings)");
        try {
            this.taxSettings = settings;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCalculationService.class,
                    "setTaxSettings(com.gokulsweets.restaurant.tax.TaxCollectionSettings)");
        }
    }

    /**
     * Taxs enabled.
     *
     * @return the tax enabled result
     */
    private boolean taxEnabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderCalculationService.class, "taxEnabled()");
        try {
            return taxSettings == null || taxSettings.enabled();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderCalculationService.class, "taxEnabled()");
        }
    }

    private static final int MONEY_SCALE = AppConstant.ORDER_CALCULATION_SERVICE_MONEY_SCALE;

    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");

    /**
     * Calculates the operation.
     *
     * @param validatedOrder the validated order
     * @return the calculate result
     */
    public OrderCalculationResult calculate(ValidatedOrderData validatedOrder) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderCalculationService.class, "calculate(ValidatedOrderData)");
        try {
            log.debug(
                    "Calculating order totals: branchId={}, pickupSlotId={}, pickupType={},"
                            + " itemCount={}",
                    validatedOrder.branch().getId(),
                    validatedOrder.pickupSlot().getId(),
                    validatedOrder.pickupType(),
                    validatedOrder.items().size());
            BigDecimal fee =
                    validatedOrder.pickupType() == PickupType.NORMAL
                            ? money(validatedOrder.branch().getPickupConvenienceFee())
                            : money(BigDecimal.ZERO);
            boolean collectTax = taxEnabled();
            BigDecimal rate =
                    collectTax && validatedOrder.pickupType() == PickupType.NORMAL
                            ? validatedOrder.branch().getPickupConvenienceFeeTaxRate()
                            : BigDecimal.ZERO;
            BigDecimal feeTax =
                    fee.subtract(
                            fee.multiply(ONE_HUNDRED)
                                    .divide(ONE_HUNDRED.add(rate), MONEY_SCALE, ROUNDING_MODE));
            return withPaymentFee(
                    calculateItems(
                            validatedOrder.items(),
                            determinePriorityCharge(validatedOrder),
                            fee,
                            feeTax,
                            validatedOrder.branch().getPickupFeeVersion(),
                            collectTax,
                            money(rate)),
                    validatedOrder.branch(),
                    BigDecimal.ZERO,
                    collectTax);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCalculationService.class,
                    "calculate(ValidatedOrderData)");
        }
    }

    /**
     * Delivery uses the same accepted branch prices, weights and taxes, with no pickup priority
     * charge.
     *
     * @return the operation result
     */
    public boolean collectingTax() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderCalculationService.class, "collectingTax()");
        try {
            return taxEnabled();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderCalculationService.class, "collectingTax()");
        }
    }

    /**
     * Calculates delivery.
     *
     * @param items the items
     * @return the calculate delivery result
     */
    public OrderCalculationResult calculateDelivery(List<ValidatedOrderItem> items) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCalculationService.class,
                        "calculateDelivery(List<ValidatedOrderItem>)");
        try {
            return calculateDelivery(items, taxEnabled());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCalculationService.class,
                    "calculateDelivery(List<ValidatedOrderItem>)");
        }
    }

    /**
     * Calculates delivery.
     *
     * @param items the items
     * @param collectTax the collect tax
     * @return the calculate delivery result
     */
    public OrderCalculationResult calculateDelivery(
            List<ValidatedOrderItem> items, boolean collectTax) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCalculationService.class,
                        "calculateDelivery(List<ValidatedOrderItem>,boolean)");
        try {
            if (items == null || items.isEmpty() || items.size() > 50)
                throw new IllegalArgumentException("Select between 1 and 50 delivery items.");
            return calculateItems(
                    items,
                    money(BigDecimal.ZERO),
                    money(BigDecimal.ZERO),
                    money(BigDecimal.ZERO),
                    0,
                    collectTax,
                    money(BigDecimal.ZERO));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCalculationService.class,
                    "calculateDelivery(List<ValidatedOrderItem>,boolean)");
        }
    }

    /**
     * Withs payment fee.
     *
     * @param price the price
     * @param branch the branch
     * @param extraBase the extra base
     * @return the with payment fee result
     */
    public OrderCalculationResult withPaymentFee(
            OrderCalculationResult price,
            com.gokulsweets.restaurant.branch.Branch branch,
            BigDecimal extraBase) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCalculationService.class,
                        "withPaymentFee(OrderCalculationResult,com.gokulsweets.restaurant.branch.Branch,BigDecimal)");
        try {
            return withPaymentFee(price, branch, extraBase, taxEnabled());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCalculationService.class,
                    "withPaymentFee(OrderCalculationResult,com.gokulsweets.restaurant.branch.Branch,BigDecimal)");
        }
    }

    /**
     * Withs payment fee.
     *
     * @param price the price
     * @param branch the branch
     * @param extraBase the extra base
     * @param collectTax the collect tax
     * @return the with payment fee result
     */
    public OrderCalculationResult withPaymentFee(
            OrderCalculationResult price,
            com.gokulsweets.restaurant.branch.Branch branch,
            BigDecimal extraBase,
            boolean collectTax) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCalculationService.class,
                        "withPaymentFee(OrderCalculationResult,com.gokulsweets.restaurant.branch.Branch,BigDecimal,boolean)");
        try {
            BigDecimal rate =
                    money(
                            branch.isOnlinePaymentFeeEnabled()
                                    ? branch.getOnlinePaymentFeeRate()
                                    : BigDecimal.ZERO);
            BigDecimal taxRate =
                    money(collectTax ? branch.getOnlinePaymentFeeTaxRate() : BigDecimal.ZERO);
            BigDecimal paymentFee = PaymentFeePricing.fee(price.totalAmount().add(extraBase), rate);
            return new OrderCalculationResult(
                    price.items(),
                    price.subtotal(),
                    price.taxAmount(),
                    price.priorityCharge(),
                    price.convenienceFee(),
                    price.convenienceFeeTax(),
                    price.totalAmount().add(paymentFee),
                    branch.getPickupFeeVersion(),
                    price.convenienceFeeTaxRate(),
                    paymentFee,
                    PaymentFeePricing.tax(paymentFee, taxRate),
                    rate,
                    taxRate);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCalculationService.class,
                    "withPaymentFee(OrderCalculationResult,com.gokulsweets.restaurant.branch.Branch,BigDecimal,boolean)");
        }
    }

    /**
     * Calculates items.
     *
     * @param items the items
     * @param priorityCharge the priority charge
     * @param fee the fee
     * @param feeTax the fee tax
     * @param feeVersion the fee version
     * @param collectTax the collect tax
     * @param feeTaxRate the fee tax rate
     * @return the calculate items result
     */
    private OrderCalculationResult calculateItems(
            List<ValidatedOrderItem> items,
            BigDecimal priorityCharge,
            BigDecimal fee,
            BigDecimal feeTax,
            long feeVersion,
            boolean collectTax,
            BigDecimal feeTaxRate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCalculationService.class,
                        "calculateItems(List<ValidatedOrderItem>,BigDecimal,BigDecimal,BigDecimal,long,boolean,BigDecimal)");
        try {
            BigDecimal subtotal = BigDecimal.ZERO;
            BigDecimal totalTax = BigDecimal.ZERO;
            List<CalculatedOrderItem> calculatedItems = new ArrayList<>();
            for (ValidatedOrderItem validatedItem : items) {
                CalculatedOrderItem item = calculateItem(validatedItem, collectTax);
                calculatedItems.add(item);
                subtotal = subtotal.add(lineSubtotal(item));
                totalTax = totalTax.add(item.taxAmount());
            }
            subtotal = money(subtotal);
            totalTax = money(totalTax);
            BigDecimal totalAmount = money(subtotal.add(totalTax).add(priorityCharge).add(fee));
            log.debug(
                    "Order calculation completed: subtotal={}, taxAmount={}, priorityCharge={},"
                            + " totalAmount={}",
                    subtotal,
                    totalTax,
                    priorityCharge,
                    totalAmount);
            return new OrderCalculationResult(
                    List.copyOf(calculatedItems),
                    subtotal,
                    totalTax,
                    priorityCharge,
                    fee,
                    feeTax,
                    totalAmount,
                    feeVersion,
                    feeTaxRate);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCalculationService.class,
                    "calculateItems(List<ValidatedOrderItem>,BigDecimal,BigDecimal,BigDecimal,long,boolean,BigDecimal)");
        }
    }

    /**
     * Calculates item.
     *
     * @param validatedItem the validated item
     * @param collectTax the collect tax
     * @return the calculate item result
     */
    private CalculatedOrderItem calculateItem(
            ValidatedOrderItem validatedItem, boolean collectTax) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCalculationService.class, "calculateItem(ValidatedOrderItem,boolean)");
        try {
            BranchProduct branchProduct = validatedItem.branchProduct();
            BigDecimal unitPrice =
                    money(
                            branchProduct.getPriceOverride() != null
                                    ? branchProduct.getPriceOverride()
                                    : validatedItem.product().getBasePrice());
            BigDecimal lineSubtotal =
                    validatedItem.saleMode() == ProductSaleMode.WEIGHT
                            ? unitPrice
                                    .multiply(BigDecimal.valueOf(validatedItem.weightGrams()))
                                    .divide(ONE_THOUSAND, MONEY_SCALE, ROUNDING_MODE)
                            : unitPrice
                                    .multiply(BigDecimal.valueOf(validatedItem.quantity()))
                                    .setScale(MONEY_SCALE, ROUNDING_MODE);
            BigDecimal taxRate =
                    collectTax
                            ? determineTaxRate(validatedItem.product().getTaxCategory())
                            : money(BigDecimal.ZERO);
            BigDecimal taxAmount =
                    lineSubtotal.multiply(taxRate).divide(ONE_HUNDRED, MONEY_SCALE, ROUNDING_MODE);
            BigDecimal lineTotal = lineSubtotal.add(taxAmount).setScale(MONEY_SCALE, ROUNDING_MODE);
            log.debug(
                    "Order item calculated: productId={}, saleMode={}, quantity={}, weightGrams={},"
                            + " unitPrice={}, taxRate={}, taxAmount={}, lineTotal={}",
                    validatedItem.product().getId(),
                    validatedItem.saleMode(),
                    validatedItem.quantity(),
                    validatedItem.weightGrams(),
                    unitPrice,
                    taxRate,
                    taxAmount,
                    lineTotal);
            return new CalculatedOrderItem(
                    validatedItem.product(),
                    validatedItem.saleMode(),
                    validatedItem.quantity(),
                    validatedItem.weightGrams(),
                    unitPrice,
                    taxRate,
                    taxAmount,
                    lineTotal);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCalculationService.class,
                    "calculateItem(ValidatedOrderItem,boolean)");
        }
    }

    /**
     * Lines subtotal.
     *
     * @param item the item
     * @return the line subtotal result
     */
    private BigDecimal lineSubtotal(CalculatedOrderItem item) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCalculationService.class, "lineSubtotal(CalculatedOrderItem)");
        try {
            if (item.saleMode() == ProductSaleMode.WEIGHT) {
                return item.unitPrice()
                        .multiply(BigDecimal.valueOf(item.weightGrams()))
                        .divide(ONE_THOUSAND, MONEY_SCALE, ROUNDING_MODE);
            }
            return item.unitPrice()
                    .multiply(BigDecimal.valueOf(item.quantity()))
                    .setScale(MONEY_SCALE, ROUNDING_MODE);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCalculationService.class,
                    "lineSubtotal(CalculatedOrderItem)");
        }
    }

    /**
     * Determines tax rate.
     *
     * @param taxCategory the tax category
     * @return the determine tax rate result
     */
    private BigDecimal determineTaxRate(TaxCategory taxCategory) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderCalculationService.class, "determineTaxRate(TaxCategory)");
        try {
            if (taxCategory == null) {
                return money(BigDecimal.ZERO);
            }
            BigDecimal cgst =
                    taxCategory.getCgstRate() != null ? taxCategory.getCgstRate() : BigDecimal.ZERO;
            BigDecimal sgst =
                    taxCategory.getSgstRate() != null ? taxCategory.getSgstRate() : BigDecimal.ZERO;
            return money(cgst.add(sgst));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCalculationService.class,
                    "determineTaxRate(TaxCategory)");
        }
    }

    /**
     * Determines priority charge.
     *
     * @param validatedOrder the validated order
     * @return the determine priority charge result
     */
    private BigDecimal determinePriorityCharge(ValidatedOrderData validatedOrder) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderCalculationService.class,
                        "determinePriorityCharge(ValidatedOrderData)");
        try {
            if (validatedOrder.pickupType() != PickupType.PRIORITY) {
                return money(BigDecimal.ZERO);
            }
            BigDecimal priorityCharge = validatedOrder.pickupSlot().getPriorityCharge();
            if (priorityCharge == null) {
                log.error(
                        "Priority pickup slot has null priority charge: pickupSlotId={}",
                        validatedOrder.pickupSlot().getId());
                throw new IllegalStateException("Priority pickup pricing is not configured.");
            }
            return money(priorityCharge);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderCalculationService.class,
                    "determinePriorityCharge(ValidatedOrderData)");
        }
    }

    /**
     * Money the operation.
     *
     * @param value the value
     * @return the money result
     */
    private BigDecimal money(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderCalculationService.class, "money(BigDecimal)");
        try {
            return value.setScale(MONEY_SCALE, ROUNDING_MODE);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderCalculationService.class, "money(BigDecimal)");
        }
    }
}
