package com.gokulsweets.restaurant.customer.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.order.entity.Order;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

class CustomerVisitPolicyTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final CustomerVisitPolicy policy = spy(new CustomerVisitPolicy(jdbc));
    final UUID subject = UUID.randomUUID();

    @Test
    void manyOffersUseOneRuleReadAndOneVisitCountWithFreshResults() throws Exception {
        var order = new Order();
        order.setVerifiedOfferSubject(new VerifiedOrderOwnership.Subject("DEV", subject));
        doAnswer(
                        call -> {
                            RowCallbackHandler handler = call.getArgument(1);
                            for (long id : List.of(1L, 2L, 3L)) {
                                var rs = mock(ResultSet.class);
                                when(rs.getLong(1)).thenReturn(id);
                                when(rs.getInt(2)).thenReturn((int) id);
                                handler.processRow(rs);
                            }
                            return null;
                        })
                .when(jdbc)
                .query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        doReturn(2L).when(policy).completed("DEV", subject);
        assertThat(
                        policy.eligibleOffers(
                                List.of(4L, 3L, 2L, 1L, 2L),
                                order,
                                CustomerVisitPolicy.SelectionMode.PREVIEW))
                .containsExactlyInAnyOrder(1L, 2L, 4L);
        verify(jdbc, times(1))
                .query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        verify(policy, times(1)).completed("DEV", subject);
        doReturn(0L).when(policy).completed("DEV", subject);
        assertThat(
                        policy.eligibleOffers(
                                List.of(1L, 2L, 3L, 4L),
                                order,
                                CustomerVisitPolicy.SelectionMode.PREVIEW))
                .containsExactly(4L);
        verify(jdbc, times(2))
                .query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        verify(policy, times(2)).completed("DEV", subject);
    }

    @Test
    void previewDoesNotLockEvenInsideWritableTransactionAndAcceptanceLocksParents() {
        var order = new Order();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
            assertThat(
                            policy.eligibleOffers(
                                    List.of(2L, 1L),
                                    order,
                                    CustomerVisitPolicy.SelectionMode.PREVIEW))
                    .containsExactlyInAnyOrder(1L, 2L);
            verify(jdbc, never()).queryForList(anyString(), eq(Long.class), any(Object[].class));
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            assertThat(
                            policy.eligibleOffers(
                                    List.of(2L, 1L),
                                    order,
                                    CustomerVisitPolicy.SelectionMode.PREVIEW))
                    .containsExactlyInAnyOrder(1L, 2L);
            verify(jdbc, never()).queryForList(anyString(), eq(Long.class), any(Object[].class));
            assertThat(
                            policy.eligibleOffers(
                                    List.of(2L, 1L),
                                    order,
                                    CustomerVisitPolicy.SelectionMode.ACCEPTANCE))
                    .containsExactlyInAnyOrder(1L, 2L);
            verify(jdbc)
                    .queryForList(
                            contains("ORDER BY id FOR SHARE"),
                            eq(Long.class),
                            eq(new Object[] {1L, 2L}));
            verify(policy, never()).completed(anyString(), any());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        }
    }
}
