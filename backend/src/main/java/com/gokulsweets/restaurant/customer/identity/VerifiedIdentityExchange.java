package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/** Internal exchange only. The customer API must supply environment from server configuration. */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerifiedIdentityExchange {

    private final Msg91WidgetProofVerifier verifier;

    private final VerifiedIdentityIssuance issuance;

    private final IdentityExchangeRateLimiter rateLimiter;

    /**
     * Exchanges the operation.
     *
     * @param environment the environment
     * @param sourceAddress the source address
     * @param deviceToken the device token
     * @param accessToken the access token
     * @param now the now
     * @return the exchange result
     */
    public VerifiedCustomerSessionStore.IssuedSession exchange(
            ConsentEnvironment environment,
            String sourceAddress,
            String deviceToken,
            String accessToken,
            Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedIdentityExchange.class,
                        "exchange(ConsentEnvironment,String,String,String,Instant)");
        try {
            return exchange(environment, sourceAddress, deviceToken, accessToken, null, now);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedIdentityExchange.class,
                    "exchange(ConsentEnvironment,String,String,String,Instant)");
        }
    }

    /**
     * Exchanges the operation.
     *
     * @param environment the environment
     * @param sourceAddress the source address
     * @param deviceToken the device token
     * @param accessToken the access token
     * @param previousSessionToken the previous session token
     * @param now the now
     * @return the exchange result
     */
    public VerifiedCustomerSessionStore.IssuedSession exchange(
            ConsentEnvironment environment,
            String sourceAddress,
            String deviceToken,
            String accessToken,
            String previousSessionToken,
            Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedIdentityExchange.class,
                        "exchange(ConsentEnvironment,String,String,String,String,Instant)");
        try {
            Objects.requireNonNull(environment);
            Objects.requireNonNull(now);
            try {
                rateLimiter.checkSource(environment, sourceAddress, now);
                rateLimiter.checkDevice(environment, deviceToken, now);
            } catch (IllegalStateException e) {
                log.warn("Customer identity exchange unavailable: rate limit configuration");
                throw e;
            }
            // Network verification precedes the short database transaction.
            String verifiedPhone;
            try {
                verifiedPhone = verifier.verifiedPhone(accessToken);
            } catch (IllegalStateException e) {
                log.warn("Customer identity exchange unavailable: provider verification");
                throw e;
            }
            try {
                rateLimiter.checkVerifiedPhone(environment, verifiedPhone, now);
            } catch (IllegalStateException e) {
                log.warn(
                        "Customer identity exchange unavailable: verified phone rate limit"
                                + " configuration");
                throw e;
            }
            try {
                return issuance.issue(
                        environment, accessToken, verifiedPhone, previousSessionToken, now);
            } catch (IllegalStateException e) {
                log.warn("Customer identity exchange unavailable: session issuance");
                throw e;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedIdentityExchange.class,
                    "exchange(ConsentEnvironment,String,String,String,String,Instant)");
        }
    }
}
