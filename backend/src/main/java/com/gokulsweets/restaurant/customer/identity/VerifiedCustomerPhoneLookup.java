package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Reads only the verified phone belonging to the current environment and session subject. */
@Service
@RequiredArgsConstructor
public class VerifiedCustomerPhoneLookup {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Optional<String> verifiedPhone(ConsentEnvironment environment, UUID subjectId) {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(subjectId);
        return jdbc.query("""
                SELECT verified_phone FROM verified_customer_subjects
                WHERE environment = ? AND id = ?
                """, rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(),
                environment.name(), subjectId);
    }
}
