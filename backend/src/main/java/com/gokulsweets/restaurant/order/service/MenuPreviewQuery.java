package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/** Applies database deadlines only to advisory menu reads, including sharing fallbacks. */
@Service
public class MenuPreviewQuery {
    private final CartAvailabilityService service;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    /** Creates an independent read-only transaction so preview deadlines cannot affect checkout. */
    public MenuPreviewQuery(
            CartAvailabilityService service,
            JdbcTemplate jdbc,
            PlatformTransactionManager manager) {
        this.service = service;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(AppConstant.MENU_PREVIEW_TIMEOUT_SECONDS);
    }

    /**
     * Reads fresh advisory availability with a transaction deadline and PostgreSQL statement cap.
     * SET LOCAL is reset on commit or rollback before this connection returns to the pool.
     * Connection acquisition retains the pool's configured timeout; no worker threads are added.
     *
     * @param branchId selected branch
     * @param date selected pickup date
     * @param items complete ordered batch
     * @return the advisory availability snapshot
     */
    public CartAvailabilityService.Availability check(
            long branchId, LocalDate date, List<CreateOrderItemRequest> items) {
        long started =
                MethodTiming.start(
                        MenuPreviewQuery.class,
                        "check(long,LocalDate,List<CreateOrderItemRequest>)");
        try {
            try {
                return transaction.execute(
                        status -> {
                            jdbc.execute(
                                    "SET LOCAL statement_timeout='"
                                            + AppConstant.MENU_PREVIEW_TIMEOUT_SECONDS
                                            + "s'");
                            return service.check(branchId, date, 1, items, true);
                        });
            } catch (TransientDataAccessException
                    | TransactionTimedOutException
                    | CannotCreateTransactionException failure) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Menu availability is temporarily busy. Please retry.",
                        failure);
            }
        } finally {
            MethodTiming.finish(
                    started,
                    MenuPreviewQuery.class,
                    "check(long,LocalDate,List<CreateOrderItemRequest>)");
        }
    }
}
