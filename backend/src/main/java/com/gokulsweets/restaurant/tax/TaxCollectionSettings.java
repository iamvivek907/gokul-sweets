package com.gokulsweets.restaurant.tax;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Backend tax collection settings contract and implementation. */
@Service
@RequiredArgsConstructor
public class TaxCollectionSettings {

    private final JdbcTemplate jdbc;

    /**
     * Returns whether the configured prerequisites for this feature are enabled.
     *
     * <p>Reads {@code tax_collection_settings}.
     *
     * @return the {@code boolean} result
     */
    public boolean enabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(TaxCollectionSettings.class, "enabled()");
        try {
            return Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT enabled FROM tax_collection_settings WHERE id=1",
                            Boolean.class));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, TaxCollectionSettings.class, "enabled()");
        }
    }

    /** Keep every tax-setting read in quote approval consistent until its transaction completes. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForQuoteApproval() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(TaxCollectionSettings.class, "lockForQuoteApproval()");
        try {
            jdbc.queryForObject(
                    "SELECT enabled FROM tax_collection_settings WHERE id=1 FOR SHARE",
                    Boolean.class);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    TaxCollectionSettings.class,
                    "lockForQuoteApproval()");
        }
    }

    /**
     * Persists tax collection settings data and returns the {@code boolean} result.
     *
     * <p>Reads {@code tax_collection_settings}.
     *
     * <p>Writes {@code tax_collection_audit}, {@code tax_collection_settings}.
     *
     * @param enabled the enabled supplied to this method
     * @param staffId the staff id supplied to this method
     * @return the value of {@code enabled}
     */
    @Transactional
    public boolean save(boolean enabled, long staffId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(TaxCollectionSettings.class, "save(boolean,long)");
        try {
            jdbc.queryForObject(
                    "SELECT enabled FROM tax_collection_settings WHERE id=1 FOR UPDATE",
                    Boolean.class);
            jdbc.update(
                    "UPDATE tax_collection_settings SET enabled=?, updated_at=CURRENT_TIMESTAMP,"
                            + " updated_by=? WHERE id=1",
                    enabled,
                    staffId);
            jdbc.update(
                    "INSERT INTO tax_collection_audit(enabled,changed_by) VALUES (?,?)",
                    enabled,
                    staffId);
            return enabled;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, TaxCollectionSettings.class, "save(boolean,long)");
        }
    }
}
