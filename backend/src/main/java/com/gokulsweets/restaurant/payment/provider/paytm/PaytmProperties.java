package com.gokulsweets.restaurant.payment.provider.paytm;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.annotation.PostConstruct;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Backend paytm properties contract and implementation. */
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

    /** Logs config lengths. */
    @PostConstruct
    public void logConfigLengths() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaytmProperties.class, "logConfigLengths()");
        try {
            log.info(
                    "Paytm config loaded: environment={}, midLength={}, merchantKeyLength={}",
                    environment,
                    mid != null ? mid.length() : null,
                    merchantKey != null ? merchantKey.length() : null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaytmProperties.class, "logConfigLengths()");
        }
    }

    /** Logs config. */
    @PostConstruct
    public void logConfig() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaytmProperties.class, "logConfig()");
        try {
            log.info(
                    "Paytm config loaded: environment={}, mid={}, midLength={},"
                            + " merchantKeyLength={}, websiteName={}, baseUrl={}",
                    environment,
                    mid,
                    mid != null ? mid.length() : null,
                    merchantKey != null ? merchantKey.length() : null,
                    websiteName,
                    baseUrl);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, PaytmProperties.class, "logConfig()");
        }
    }
}
