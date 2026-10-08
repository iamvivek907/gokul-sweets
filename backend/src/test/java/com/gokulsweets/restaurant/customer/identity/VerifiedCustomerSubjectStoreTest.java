package com.gokulsweets.restaurant.customer.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

class VerifiedCustomerSubjectStoreTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final VerifiedCustomerSubjectStore store =
            new VerifiedCustomerSubjectStore(
                    jdbc, new org.springframework.mock.env.MockEnvironment());

    @Test
    void rejectsUnverifiedPhoneShapesWithoutDatabaseWrites() {
        for (var phone : new String[] {"9876543210", "+447700900000", "+910123456789", ""}) {
            assertThatThrownBy(
                            () ->
                                    store.recordVerifiedPhone(
                                            ConsentEnvironment.PROD, phone, Instant.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(jdbc);
    }
}
