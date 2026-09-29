package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.customer.identity.VerifiedCustomerPhoneLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Called inside the verified final-payment transaction; no partially paid standard order is exposed. */
@Service
@RequiredArgsConstructor
public class OccasionOrderFinalizer {
    private final JdbcTemplate jdbc;
    private final VerifiedCustomerPhoneLookup customers;

    public String create(UUID enquiryId, ConsentEnvironment environment, UUID subject,
                         long branchId, long pickupSlotId, BigDecimal gross) {
        Long existing = jdbc.query("SELECT order_id FROM occasion_enquiries WHERE id = ?",
                rs -> rs.next() ? rs.getObject(1, Long.class) : null, enquiryId);
        if (existing != null) return jdbc.queryForObject("SELECT order_number FROM orders WHERE id = ?", String.class, existing);
        var lines = jdbc.query("""
                SELECT product_id, product_name, sale_mode, quantity, weight_grams, gross_amount,
                       subtotal, tax_amount, cgst_rate + sgst_rate
                FROM occasion_quote_lines WHERE enquiry_id = ? ORDER BY product_id
                """, (rs, row) -> new Line(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getInt(4), rs.getObject(5, Integer.class), rs.getBigDecimal(6),
                rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9)), enquiryId);
        if (lines.isEmpty() || lines.stream().map(Line::gross).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(gross) != 0)
            throw new IllegalStateException("Approved item prices do not match the verified occasion payment.");
        String phone = customers.verifiedPhone(environment, subject)
                .orElseThrow(() -> new IllegalStateException("Verified customer phone is unavailable."));
        String name = customers.displayName(environment, subject).filter(value -> !value.isBlank())
                .orElse("Occasion customer");
        String number = "GKS-OCC-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        BigDecimal subtotal = lines.stream().map(Line::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = lines.stream().map(Line::tax).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate serviceDate = jdbc.query("SELECT service_date FROM occasion_enquiries WHERE id = ?",
                rs -> rs.next() ? rs.getDate(1).toLocalDate() : null, enquiryId);
        Long orderId = jdbc.queryForObject("""
                INSERT INTO orders(order_number, branch_id, pickup_slot_id, customer_name, customer_phone,
                    customer_phone_normalized, pickup_type, subtotal, tax_amount, total_amount,
                    order_status, reservation_expires_at)
                VALUES (?, ?, ?, ?, ?, ?, 'NORMAL', ?, ?, ?, 'CONFIRMED', ?) RETURNING id
                """, Long.class, number, branchId, pickupSlotId, name, phone, phone,
                subtotal, tax, gross, Timestamp.valueOf(serviceDate.atTime(LocalTime.of(23, 59, 59))));
        for (Line line : lines) {
            BigDecimal divisor = "WEIGHT".equals(line.mode())
                    ? BigDecimal.valueOf(line.weightGrams()).movePointLeft(3)
                    : BigDecimal.valueOf(line.quantity());
            BigDecimal unitPrice = line.subtotal().divide(divisor, 2, RoundingMode.HALF_UP);
            jdbc.update("""
                    INSERT INTO order_items(order_id, product_id, product_name, sale_mode, quantity,
                        weight_grams, unit_price, tax_rate, tax_amount, line_total)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, orderId, line.productId(), line.name(), line.mode(), line.quantity(),
                    line.weightGrams(), unitPrice, line.taxRate(), line.tax(), line.gross());
        }
        jdbc.update("""
                INSERT INTO verified_order_ownership(order_id, environment, verified_subject_id)
                VALUES (?, ?, ?)
                """, orderId, environment.name(), subject);
        jdbc.update("""
                INSERT INTO payments(order_id, provider, provider_payment_id, provider_order_id,
                    amount, currency, payment_status, paid_at)
                SELECT ?, 'PHONEPE', provider_transaction_id, merchant_order_id, amount, 'INR', 'PAID',
                       (paid_at AT TIME ZONE 'Asia/Kolkata')
                FROM occasion_payment_attempts WHERE enquiry_id = ? AND status = 'PAID'
                """, orderId, enquiryId);
        jdbc.update("UPDATE occasion_enquiries SET order_id = ? WHERE id = ?", orderId, enquiryId);
        return number;
    }

    private record Line(long productId, String name, String mode, int quantity, Integer weightGrams,
                        BigDecimal gross, BigDecimal subtotal, BigDecimal tax, BigDecimal taxRate) {}
}
