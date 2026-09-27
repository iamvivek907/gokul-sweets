package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.service.model.OrderCalculationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** Internal signed delivery price. Public preview wiring remains gated until delivery checkout launches. */
@Service
public class DeliveryAcceptedQuoteService {
    private final EnhancementProperties flags;
    private final DeliveryOrderPreparationService preparation;
    private final Clock clock;

    @Value("${checkout.quote-signing-key:}")
    private String signingKey;

    public DeliveryAcceptedQuoteService(EnhancementProperties flags,
                                        DeliveryOrderPreparationService preparation, Clock inventoryClock) {
        this.flags = flags;
        this.preparation = preparation;
        this.clock = inventoryClock;
    }

    public record Line(String productName, String unitPrice, String taxRate, String taxAmount,
                       String totalAmount) {}
    public record Quote(long windowId, String serviceDate, String startsAt, String endsAt,
                        List<Line> items, String subtotal, String taxAmount, String priorityCharge,
                        String totalAmount, String currency, String expiresAt, String token) {}

    @Transactional(readOnly = true)
    public Quote preview(DeliveryOrderCreationService.CreateRequest request) {
        requireEnabled();
        DeliveryOrderCreationService.validate(request);
        var prepared = preparation.prepare(request.quote(), request.windowId());
        long expiry = Instant.now(clock).plusSeconds(300).getEpochSecond();
        String token = expiry + "." + sign(expiry + ":" + payload(request, prepared.price()));
        var window = prepared.window();
        var price = prepared.price();
        return new Quote(window.id(), window.serviceDate().toString(), window.startsAt().toString(),
                window.endsAt().toString(), price.items().stream().map(item -> new Line(
                        item.product().getName(), item.unitPrice().toPlainString(),
                        item.taxRate().toPlainString(), item.taxAmount().toPlainString(),
                        item.lineTotal().toPlainString())).toList(), price.subtotal().toPlainString(),
                price.taxAmount().toPlainString(), price.priorityCharge().toPlainString(),
                price.totalAmount().toPlainString(), "INR", Instant.ofEpochSecond(expiry).toString(), token);
    }

    public void accept(DeliveryOrderCreationService.CreateRequest request, OrderCalculationResult price) {
        requireEnabled();
        String token = request.acceptedQuoteToken();
        if (token == null || !token.matches("[0-9]{10,12}\\.[A-Za-z0-9_-]{43}"))
            throw new IllegalStateException("Review the current delivery price before continuing.");
        String[] parts = token.split("\\.", 2);
        long expiry = Long.parseLong(parts[0]);
        long now = Instant.now(clock).getEpochSecond();
        if (expiry <= now || expiry > now + 300)
            throw new IllegalStateException("This delivery price expired. Review the current price again.");
        String expected = sign(parts[0] + ":" + payload(request, price));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                parts[1].getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalStateException("Your delivery details or price changed. Review the updated quote.");
    }

    private void requireEnabled() {
        if (!flags.isDeliveryAcceptedQuote() || !flags.isDeliveryRiderHolds()
                || !flags.isDeliveryAddressBoundaries())
            throw new IllegalStateException("Delivery quotes are disabled.");
    }

    private static String payload(DeliveryOrderCreationService.CreateRequest request, OrderCalculationResult price) {
        var quote = request.quote();
        StringBuilder value = new StringBuilder("delivery-price-v1|");
        append(value, Long.toString(quote.branchId()));
        append(value, Long.toString(request.windowId()));
        append(value, quote.serviceDate().toString());
        append(value, quote.locality().trim());
        append(value, quote.postalCode());
        append(value, Double.toString(quote.latitude()));
        append(value, Double.toString(quote.longitude()));
        append(value, request.customerName().trim());
        append(value, request.customerPhone());
        append(value, request.addressLine().trim());
        quote.items().forEach(item -> {
            append(value, String.valueOf(item.productId()));
            append(value, String.valueOf(item.quantity()));
            append(value, String.valueOf(item.weightGrams()));
        });
        price.items().forEach(item -> {
            append(value, String.valueOf(item.product().getId()));
            append(value, String.valueOf(item.quantity()));
            append(value, String.valueOf(item.saleMode()));
            append(value, String.valueOf(item.weightGrams()));
            append(value, item.unitPrice().toPlainString());
            append(value, item.taxRate().toPlainString());
            append(value, item.taxAmount().toPlainString());
            append(value, item.lineTotal().toPlainString());
        });
        append(value, price.subtotal().toPlainString());
        append(value, price.taxAmount().toPlainString());
        append(value, price.priorityCharge().toPlainString());
        append(value, price.totalAmount().toPlainString());
        return value.toString();
    }

    private static void append(StringBuilder value, String part) {
        value.append(part.length()).append(':').append(part);
    }

    private String sign(String message) {
        if (signingKey == null || signingKey.length() < 32)
            throw new IllegalStateException("Checkout quote signing key must contain at least 32 characters.");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Delivery quote signing failed.", exception);
        }
    }
}
