package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
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

@Service
@Slf4j
public class OrderCalculationService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");

    public OrderCalculationResult calculate(ValidatedOrderData validatedOrder) {

        log.debug(
                "Calculating order totals: branchId={}, pickupSlotId={}, pickupType={}, itemCount={}",
                validatedOrder.branch().getId(),
                validatedOrder.pickupSlot().getId(),
                validatedOrder.pickupType(),
                validatedOrder.items().size()
        );

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        List<CalculatedOrderItem> calculatedItems = new ArrayList<>();

        for (ValidatedOrderItem validatedItem : validatedOrder.items()) {
            CalculatedOrderItem item = calculateItem(validatedItem);
            calculatedItems.add(item);
            subtotal = subtotal.add(lineSubtotal(item));
            totalTax = totalTax.add(item.taxAmount());
        }

        subtotal = money(subtotal);
        totalTax = money(totalTax);
        BigDecimal priorityCharge = determinePriorityCharge(validatedOrder);
        BigDecimal totalAmount = money(
                subtotal.add(totalTax).add(priorityCharge)
        );

        log.debug(
                "Order calculation completed: subtotal={}, taxAmount={}, priorityCharge={}, totalAmount={}",
                subtotal,
                totalTax,
                priorityCharge,
                totalAmount
        );

        return new OrderCalculationResult(
                List.copyOf(calculatedItems),
                subtotal,
                totalTax,
                priorityCharge,
                totalAmount
        );
    }

    private CalculatedOrderItem calculateItem(ValidatedOrderItem validatedItem) {

        BranchProduct branchProduct = validatedItem.branchProduct();
        BigDecimal unitPrice = money(
                branchProduct.getPriceOverride() != null
                        ? branchProduct.getPriceOverride()
                        : validatedItem.product().getBasePrice()
        );

        BigDecimal lineSubtotal =
                validatedItem.saleMode() == ProductSaleMode.WEIGHT
                        ? unitPrice
                        .multiply(BigDecimal.valueOf(validatedItem.weightGrams()))
                        .divide(ONE_THOUSAND, MONEY_SCALE, ROUNDING_MODE)
                        : unitPrice
                        .multiply(BigDecimal.valueOf(validatedItem.quantity()))
                        .setScale(MONEY_SCALE, ROUNDING_MODE);

        BigDecimal taxRate =
                determineTaxRate(validatedItem.product().getTaxCategory());

        BigDecimal taxAmount =
                lineSubtotal
                        .multiply(taxRate)
                        .divide(ONE_HUNDRED, MONEY_SCALE, ROUNDING_MODE);

        BigDecimal lineTotal =
                lineSubtotal.add(taxAmount)
                        .setScale(MONEY_SCALE, ROUNDING_MODE);

        log.debug(
                "Order item calculated: productId={}, saleMode={}, quantity={}, weightGrams={}, unitPrice={}, taxRate={}, taxAmount={}, lineTotal={}",
                validatedItem.product().getId(),
                validatedItem.saleMode(),
                validatedItem.quantity(),
                validatedItem.weightGrams(),
                unitPrice,
                taxRate,
                taxAmount,
                lineTotal
        );

        return new CalculatedOrderItem(
                validatedItem.product(),
                validatedItem.saleMode(),
                validatedItem.quantity(),
                validatedItem.weightGrams(),
                unitPrice,
                taxRate,
                taxAmount,
                lineTotal
        );
    }

    private BigDecimal lineSubtotal(CalculatedOrderItem item) {

        if (item.saleMode() == ProductSaleMode.WEIGHT) {
            return item.unitPrice()
                    .multiply(BigDecimal.valueOf(item.weightGrams()))
                    .divide(ONE_THOUSAND, MONEY_SCALE, ROUNDING_MODE);
        }

        return item.unitPrice()
                .multiply(BigDecimal.valueOf(item.quantity()))
                .setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    private BigDecimal determineTaxRate(TaxCategory taxCategory) {

        if (taxCategory == null) {
            return money(BigDecimal.ZERO);
        }

        BigDecimal cgst =
                taxCategory.getCgstRate() != null
                        ? taxCategory.getCgstRate()
                        : BigDecimal.ZERO;

        BigDecimal sgst =
                taxCategory.getSgstRate() != null
                        ? taxCategory.getSgstRate()
                        : BigDecimal.ZERO;

        return money(cgst.add(sgst));
    }

    private BigDecimal determinePriorityCharge(ValidatedOrderData validatedOrder) {

        if (validatedOrder.pickupType() != PickupType.PRIORITY) {
            return money(BigDecimal.ZERO);
        }

        BigDecimal priorityCharge = validatedOrder.pickupSlot().getPriorityCharge();

        if (priorityCharge == null) {
            log.error(
                    "Priority pickup slot has null priority charge: pickupSlotId={}",
                    validatedOrder.pickupSlot().getId()
            );
            throw new IllegalStateException(
                    "Priority pickup pricing is not configured."
            );
        }

        return money(priorityCharge);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING_MODE);
    }
}
