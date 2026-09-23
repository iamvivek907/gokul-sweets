package com.gokulsweets.restaurant.payment.provider.phonepe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "phonepe")
@Validated
@Getter
@Setter
public class PhonePeProperties {

    @NotBlank
    private String merchantId = "";

    @NotBlank
    private String saltKey = "";

    @NotBlank
    private String saltIndex = "1";

    @NotBlank
    private String baseUrl = "https://api.phonepe.com/apis/hermes";

    @NotBlank
    private String createPaymentPath = "/pg/v1/pay";

    @NotBlank
    private String statusPathTemplate = "/pg/v1/status/{merchantId}/{merchantTransactionId}";

    private String redirectUrl = "";
    private String callbackUrl = "";

    @Min(1)
    @Max(60)
    private int connectTimeoutSeconds = 10;

    @Min(1)
    @Max(120)
    private int requestTimeoutSeconds = 20;

    public void requireApiConfiguration() {
        if (merchantId.isBlank() || saltKey.isBlank() || saltIndex.isBlank()) {
            throw new IllegalStateException(
                    "PhonePe API credentials are not configured."
            );
        }
    }
}
