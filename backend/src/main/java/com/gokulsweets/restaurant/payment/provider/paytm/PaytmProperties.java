package com.gokulsweets.restaurant.payment.provider.paytm;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConfigurationProperties(prefix = "paytm")
@Getter
@Setter
public class PaytmProperties {

    private String environment;
    private String mid;
    private String merchantKey;
    private String websiteName;
    private String baseUrl;
    private String callbackUrl;

    @PostConstruct
    public void logConfigLengths() {

        log.info(
                "Paytm config loaded: environment={}, midLength={}, merchantKeyLength={}",
                environment,
                mid != null ? mid.length() : null,
                merchantKey != null ? merchantKey.length() : null
        );
    }

    @PostConstruct
    public void logConfig() {

        log.info(
                "Paytm config loaded: environment={}, mid={}, midLength={}, merchantKeyLength={}, websiteName={}, baseUrl={}",
                environment,
                mid,
                mid != null ? mid.length() : null,
                merchantKey != null ? merchantKey.length() : null,
                websiteName,
                baseUrl
        );
    }
}
