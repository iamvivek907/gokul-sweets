package com.gokulsweets.restaurant.payment.provider;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProviderRegistryTest {

    @Test
    void fallsBackToDefaultProviderWhenRequestIsNull() {
        PaymentProviderRegistry registry = registry("RAZORPAY,PAYTM", "PAYTM");

        PaymentProvider provider = registry.require(null);

        assertThat(provider.providerType()).isEqualTo(PaymentProviderType.PAYTM);
    }

    @Test
    void rejectsDisabledProviderSelection() {
        PaymentProviderRegistry registry = registry("RAZORPAY,PAYTM", "RAZORPAY");

        assertThatThrownBy(() -> registry.require(PaymentProviderType.PHONEPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not enabled")
                .hasMessageContaining("RAZORPAY")
                .hasMessageContaining("PAYTM");
    }

    @Test
    void exposesEnabledProviderSetForSafeConfigDrivenSelection() {
        PaymentProviderRegistry registry = registry("RAZORPAY,PAYTM,PHONEPE", "PHONEPE");

        assertThat(registry.defaultProvider()).isEqualTo(PaymentProviderType.PHONEPE);
        assertThat(registry.enabledProviders())
                .containsExactlyInAnyOrder(
                        PaymentProviderType.RAZORPAY,
                        PaymentProviderType.PAYTM,
                        PaymentProviderType.PHONEPE
                );
    }

    private PaymentProviderRegistry registry(String enabled, String defaultProvider) {
        return new PaymentProviderRegistry(
                List.of(
                        provider(PaymentProviderType.RAZORPAY),
                        provider(PaymentProviderType.PAYTM),
                        provider(PaymentProviderType.PHONEPE)
                ),
                enabled,
                defaultProvider
        );
    }

    private PaymentProvider provider(PaymentProviderType type) {
        return new PaymentProvider() {
            @Override
            public PaymentProviderType providerType() {
                return type;
            }

            @Override
            public PaymentCreationResult createPayment(Order order, Payment payment) {
                return null;
            }

            @Override
            public PaymentVerificationResult verifyPayment(Payment payment) {
                return new PaymentVerificationResult(PaymentStatus.PENDING, null, null);
            }

            @Override
            public RefundResult refund(Payment payment) {
                return new RefundResult(PaymentStatus.REFUND_FAILED, null, "not supported in test");
            }

            @Override
            public RefundResult verifyRefund(Payment payment) {
                return new RefundResult(PaymentStatus.REFUND_FAILED, null, "not supported in test");
            }
        };
    }
}
