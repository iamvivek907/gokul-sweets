package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VerifiedCustomerSubjectStoreTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final VerifiedCustomerSubjectStore store = new VerifiedCustomerSubjectStore(jdbc);

    @Test
    void returnsDatabaseSubjectAndScopesLookupToEnvironment() {
        var subject = UUID.randomUUID();
        when(jdbc.queryForObject(any(String.class), eq(UUID.class), any(Object[].class)))
                .thenReturn(subject);
        assertThat(store.recordVerifiedPhone(ConsentEnvironment.DEV, "+919876543210", Instant.now()))
                .isEqualTo(subject);
        verify(jdbc).queryForObject(any(String.class), eq(UUID.class),
                any(UUID.class), eq("DEV"), eq("+919876543210"), any(), any());
    }

    @Test
    void rejectsUnverifiedPhoneShapesWithoutDatabaseWrites() {
        for (var phone : new String[] {"9876543210", "+447700900000", "+910123456789", ""}) {
            assertThatThrownBy(() -> store.recordVerifiedPhone(ConsentEnvironment.PROD, phone, Instant.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(jdbc);
    }
}
