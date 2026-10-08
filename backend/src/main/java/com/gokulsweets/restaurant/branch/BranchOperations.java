package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Backend branch operations contract and implementation. */
@Service
@RequiredArgsConstructor
public class BranchOperations {

    private final JdbcTemplate jdbc;

    private final StaffAuthorizationService authorization;

    /** Immutable status data contract. */
    public record Status(boolean operational) {}

    /**
     * Authorizes the operation.
     *
     * @param id the id
     */
    private void authorize(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOperations.class, "authorize(long)");
        try {
            authorization.requirePermission(PermissionName.BRANCH_MANAGE);
            authorization.requireBranchAccess(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchOperations.class, "authorize(long)");
        }
    }

    /**
     * Returns the operation.
     *
     * @param id the id
     * @return the get result
     */
    @Transactional(readOnly = true)
    public Status get(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOperations.class, "get(long)");
        try {
            authorize(id);
            return status(id);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BranchOperations.class, "get(long)");
        }
    }

    /**
     * Updates the operation.
     *
     * @param id the id
     * @param input the input
     * @return the set result
     */
    @Transactional
    public Status set(long id, Status input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOperations.class, "set(long,Status)");
        try {
            authorize(id);
            if (input == null) throw new IllegalArgumentException("Choose an operational status.");
            jdbc.queryForObject("SELECT id FROM branches WHERE id=? FOR UPDATE", Long.class, id);
            jdbc.update(
                    "UPDATE branches SET operational=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    input.operational(),
                    id);
            return status(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchOperations.class, "set(long,Status)");
        }
    }

    /**
     * Statuses the operation.
     *
     * @param id the id
     * @return the status result
     */
    private Status status(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOperations.class, "status(long)");
        try {
            return jdbc.queryForObject(
                    "SELECT operational FROM branches WHERE id=?",
                    (r, n) -> new Status(r.getBoolean(1)),
                    id);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BranchOperations.class, "status(long)");
        }
    }
}
