package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.customer.identity.VerifiedCustomerPhoneLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OccasionEnquiryServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EnhancementProperties features = new EnhancementProperties();
    private final VerifiedCustomerPhoneLookup phones = mock(VerifiedCustomerPhoneLookup.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-29T06:00:00Z"), ZoneOffset.UTC);
    private OccasionEnquiryService service;

    @Test
    void quoteRequiresPositiveDepositAndBalanceDeadlineBeforeAnyDatabaseWrite() {
        features.setOccasionEnquiries(true);
        var expires = clock.instant().plusSeconds(3600);
        assertThatThrownBy(() -> service.quote(ConsentEnvironment.DEV, 1, UUID.randomUUID(), "manager",
                new OccasionEnquiryService.Quote(new BigDecimal("1000.00"), BigDecimal.ZERO,
                        expires, null, "Pickup", List.of()))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.quote(ConsentEnvironment.DEV, 1, UUID.randomUUID(), "manager",
                new OccasionEnquiryService.Quote(new BigDecimal("1000.00"), new BigDecimal("200.00"),
                        expires, null, "Pickup", List.of()))).isInstanceOf(ResponseStatusException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    private final UUID subject = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OccasionEnquiryService(jdbc, features, phones, clock);
    }

    private OccasionEnquiryService.Request request(LocalDate date, OccasionEnquiryService.Fulfilment mode,
                                                   String address, BigDecimal quantity) {
        return new OccasionEnquiryService.Request(1, "Birthday", date, 20, mode, address, null,
                List.of(new OccasionEnquiryService.Item(4, quantity, OccasionEnquiryService.Unit.PIECE, null)));
    }

    @Test
    void featureIsOffUntilExplicitlyEnabled() {
        assertThatThrownBy(() -> service.submit(ConsentEnvironment.DEV, subject,
                request(LocalDate.of(2026, 10, 1), OccasionEnquiryService.Fulfilment.PICKUP, null, BigDecimal.ONE)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) error)
                        .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsPastDateAndDeliveryWithoutAddressBeforeAnyWrites() {
        features.setOccasionEnquiries(true);
        assertThatThrownBy(() -> service.submit(ConsentEnvironment.DEV, subject,
                request(LocalDate.of(2026, 9, 29), OccasionEnquiryService.Fulfilment.PICKUP, null, BigDecimal.ONE)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.submit(ConsentEnvironment.DEV, subject,
                request(LocalDate.of(2026, 10, 1), OccasionEnquiryService.Fulfilment.DELIVERY_REQUEST, null, BigDecimal.ONE)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsFractionalPieceAndUnavailableProductBeforeInsertion() {
        features.setOccasionEnquiries(true);
        when(phones.verifiedPhone(ConsentEnvironment.DEV, subject)).thenReturn(Optional.of("+919876543210"));
        assertThatThrownBy(() -> service.submit(ConsentEnvironment.DEV, subject,
                request(LocalDate.of(2026, 10, 1), OccasionEnquiryService.Fulfilment.PICKUP,
                        null, new BigDecimal("1.5"))))
                .isInstanceOf(ResponseStatusException.class);
        when(jdbc.queryForObject(contains("FROM branches"), eq(Boolean.class), any())).thenReturn(true);
        when(jdbc.queryForObject(contains("FROM branch_products"), eq(Boolean.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(false);
        assertThatThrownBy(() -> service.submit(ConsentEnvironment.DEV, subject,
                request(LocalDate.of(2026, 10, 1), OccasionEnquiryService.Fulfilment.PICKUP, null, BigDecimal.ONE)))
                .isInstanceOf(ResponseStatusException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void limitsVerifiedCustomerToThreeRequestsPerDayBeforeWriting() {
        features.setOccasionEnquiries(true);
        when(phones.verifiedPhone(ConsentEnvironment.DEV, subject)).thenReturn(Optional.of("+919876543210"));
        when(jdbc.queryForObject(contains("count(*) FROM occasion_enquiries"), eq(Integer.class),
                eq("DEV"), eq(subject), any())).thenReturn(3);

        assertThatThrownBy(() -> service.submit(ConsentEnvironment.DEV, subject,
                request(LocalDate.of(2026, 10, 1), OccasionEnquiryService.Fulfilment.PICKUP, null, BigDecimal.ONE)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) error)
                        .getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
