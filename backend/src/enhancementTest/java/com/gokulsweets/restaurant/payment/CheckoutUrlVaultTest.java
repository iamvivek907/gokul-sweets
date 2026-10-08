package com.gokulsweets.restaurant.payment;

import static org.assertj.core.api.Assertions.*;

import com.gokulsweets.restaurant.payment.service.CheckoutUrlVault;

import org.junit.jupiter.api.Test;

class CheckoutUrlVaultTest {
    @Test
    void linksSurviveReloadButNeverAppearInStorageAndTamperingFails() {
        var vault =
                new CheckoutUrlVault(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        String url = "https://checkout.phonepe.com/pay?token=private";
        String sealed = vault.seal(url);
        assertThat(sealed).doesNotContain("phonepe", "private");
        assertThat(vault.open(sealed)).isEqualTo(url);
        assertThat(vault.seal(url)).isNotEqualTo(sealed);
        assertThatThrownBy(() -> vault.open(sealed.substring(0, sealed.length() - 4) + "AAAA"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void insecureLinksAndMissingKeysFailClosed() {
        assertThatThrownBy(
                        () -> new CheckoutUrlVault("").seal("https://checkout.phonepe.com/token"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                new CheckoutUrlVault(
                                                java.util.Base64.getEncoder()
                                                        .encodeToString(new byte[32]))
                                        .seal("http://example.invalid/token"))
                .isInstanceOf(IllegalStateException.class);
    }
}
