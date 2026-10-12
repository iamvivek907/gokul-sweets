package com.gokulsweets.restaurant.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

class MenuPreviewQueryTest {
    private final CartAvailabilityService service = mock(CartAvailabilityService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus status = new SimpleTransactionStatus();
    private final LocalDate date = LocalDate.of(2026, 10, 10);

    @Test
    void independentReadOnlyDeadlineSetsLocalTimeoutBeforeStockReads() {
        when(manager.getTransaction(any())).thenReturn(status);
        var result = new CartAvailabilityService.Availability("PICKUP", date, date, List.of());
        doReturn(result).when(service).check(1L, date, 1, List.of(), true);
        var query = new MenuPreviewQuery(service, jdbc, manager);
        assertThat(query.check(1L, date, List.of())).isSameAs(result);
        var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        var order = inOrder(manager, jdbc, service);
        order.verify(manager).getTransaction(definition.capture());
        order.verify(jdbc).execute("SET LOCAL statement_timeout='5s'");
        order.verify(service).check(1L, date, 1, List.of(), true);
        order.verify(manager).commit(status);
        assertThat(definition.getValue().isReadOnly()).isTrue();
        assertThat(definition.getValue().getTimeout()).isEqualTo(5);
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void databaseTimeoutRollsBackAndRemainsRetryable() {
        when(manager.getTransaction(any())).thenReturn(status);
        var timeout = new org.springframework.dao.QueryTimeoutException("statement canceled");
        when(service.check(1L, date, 1, List.of(), true)).thenThrow(timeout);
        var query = new MenuPreviewQuery(service, jdbc, manager);
        assertThatThrownBy(() -> query.check(1L, date, List.of()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(503))
                .hasCause(timeout);
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
        var result = new CartAvailabilityService.Availability("PICKUP", date, date, List.of());
        doReturn(result).when(service).check(1L, date, 1, List.of(), true);
        assertThat(query.check(1L, date, List.of())).isSameAs(result);
    }

    @Test
    void unavailableConnectionIsRetryableWithoutRunningQueries() {
        when(manager.getTransaction(any()))
                .thenThrow(
                        new org.springframework.transaction.CannotCreateTransactionException(
                                "pool exhausted"));
        var query = new MenuPreviewQuery(service, jdbc, manager);
        assertThatThrownBy(() -> query.check(1L, date, List.of()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(503));
        verifyNoInteractions(jdbc, service);
    }

    @Test
    void validationErrorsKeepOriginalExceptionAndRollback() {
        when(manager.getTransaction(any())).thenReturn(status);
        var failure = new IllegalArgumentException("Branch closed");
        when(service.check(1L, date, 1, List.of(), true)).thenThrow(failure);
        var query = new MenuPreviewQuery(service, jdbc, manager);
        assertThatThrownBy(() -> query.check(1L, date, List.of())).isSameAs(failure);
        verify(manager).rollback(status);
    }
}
