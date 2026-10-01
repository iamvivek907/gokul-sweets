package com.gokulsweets.restaurant.tax;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaxCollectionSettings {
    private final JdbcTemplate jdbc;
    public boolean enabled() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT enabled FROM tax_collection_settings WHERE id=1", Boolean.class));
    }
    @Transactional
    public boolean save(boolean enabled, long staffId) {
        jdbc.queryForObject("SELECT enabled FROM tax_collection_settings WHERE id=1 FOR UPDATE", Boolean.class);
        jdbc.update("UPDATE tax_collection_settings SET enabled=?, updated_at=CURRENT_TIMESTAMP, updated_by=? WHERE id=1", enabled, staffId);
        jdbc.update("INSERT INTO tax_collection_audit(enabled,changed_by) VALUES (?,?)", enabled, staffId);
        return enabled;
    }
}
