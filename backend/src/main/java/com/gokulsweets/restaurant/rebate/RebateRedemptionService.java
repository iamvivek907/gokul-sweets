package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class RebateRedemptionService {

    private final RebateRedemptionRepository
            rebateRedemptionRepository;

    @Transactional
    public void recordRedemptionIfApplicable(
            Order order
    ) {

        if (order == null) {

            throw new IllegalArgumentException(
                    "Order is required."
            );
        }

        /*
         * No rebate was applied to this order.
         */
        if (order.getRebate() == null
                || order.getRebateCode() == null
                || order.getRebateDiscountAmount() == null
                || order.getRebateDiscountAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {

            return;
        }

        /*
         * Idempotency:
         *
         * Paytm callbacks/reconciliation may tell us
         * about the same successful payment more than once.
         */
        if (rebateRedemptionRepository
                .existsByOrderId(
                        order.getId()
                )) {

            log.debug(
                    "Rebate redemption already exists: orderId={}, orderNumber={}",
                    order.getId(),
                    order.getOrderNumber()
            );

            return;
        }

        String customerPhone =
                normalizePhone(
                        order.getCustomerPhone()
                );

        RebateRedemption redemption =
                new RebateRedemption();

        redemption.setRebate(
                order.getRebate()
        );

        redemption.setOrder(
                order
        );

        redemption.setCustomerPhone(
                customerPhone
        );

        redemption.setDiscountAmount(
                order.getRebateDiscountAmount()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
        );

        rebateRedemptionRepository.save(
                redemption
        );

        log.info(
                "Rebate redeemed: orderId={}, orderNumber={}, rebateId={}, rebateCode={}, discountAmount={}",
                order.getId(),
                order.getOrderNumber(),
                order.getRebate().getId(),
                order.getRebateCode(),
                order.getRebateDiscountAmount()
        );
    }

    private String normalizePhone(
            String phone
    ) {

        if (phone == null
                || phone.isBlank()) {

            throw new IllegalStateException(
                    "Customer phone is missing from the order."
            );
        }

        return phone
                .trim()
                .replaceAll(
                        "\\s+",
                        ""
                );
    }
}