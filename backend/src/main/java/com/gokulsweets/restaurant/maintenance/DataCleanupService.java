package com.gokulsweets.restaurant.maintenance;

import com.gokulsweets.restaurant.config.ApplicationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** An explicit allowlist, never a generic table sweeper. Orders and financial/audit records are untouched. */
@Service
public class DataCleanupService {
    private static final Logger log = LoggerFactory.getLogger(DataCleanupService.class);
    static final int LIMIT = 500;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final Clock clock;
    private final Environment environment;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    // Keep completion events: their IDs are replay/auto-acknowledgement boundaries for later refunds.
    static final String CUSTOMER = """
        FROM customer_notification_events e JOIN orders o ON o.order_number=e.target_id
        WHERE e.environment=:environment AND e.target_type='ORDER'
          AND e.kind IN ('CONFIRMED','PREPARING','READY_FOR_PICKUP','READY_FOR_DELIVERY','OUT_FOR_DELIVERY','READY_TIME_CHANGED')
          AND e.created_at<:cutoff AND e.read_at<:cutoff
          AND o.order_status IN ('PICKED_UP','DELIVERED') AND o.updated_at<:localCutoff
          AND NOT EXISTS (SELECT 1 FROM customer_push_deliveries d WHERE d.event_id=e.id
            AND (d.state NOT IN ('ACCEPTED','REVOKED','SKIPPED','FAILED') OR d.lease_until>:now))
        """;
    static final String STAFF = """
        FROM staff_order_alerts e JOIN orders o ON o.id=e.order_id
        WHERE e.environment=:environment
          AND e.kind IN ('NEW_ORDER','PREPARATION_SOON','PREPARATION_DUE','PREPARATION_OVERDUE','READY_OVERDUE')
          AND e.created_at<:cutoff AND (e.scheduled_at IS NULL OR e.scheduled_at<:localCutoff)
          AND o.order_status IN ('PICKED_UP','DELIVERED') AND o.updated_at<:localCutoff
          AND NOT EXISTS (SELECT 1 FROM staff_alert_deliveries d WHERE d.event_id=e.id
            AND (d.state NOT IN ('ACCEPTED','REVOKED','SKIPPED','FAILED') OR d.lease_until>:now))
        """;

    public DataCleanupService(JdbcTemplate jdbc, @Qualifier("inventoryClock") Clock clock,
                              Environment environment, ObjectMapper mapper, PlatformTransactionManager transactions) {
        this.jdbc=jdbc; this.named=new NamedParameterJdbcTemplate(jdbc); this.clock=clock;
        this.environment=environment; this.mapper=mapper;
        tx=new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(45);
    }
    private String scope() {
        String scope=environment.getProperty("gokul.environment-isolation.environment", "");
        if (!Set.of("DEV","PROD").contains(scope)) throw new IllegalStateException("Cleanup requires a valid deployment environment.");
        return scope;
    }
    public record Config(boolean enabled, String dailyTime, int retentionDays, long revision) {}
    public record View(Config config, String timeZone, String status, Instant startedAt, Instant finishedAt,
                       String trigger, String error, Map<String,Long> deleted, int limitPerCategory, boolean running) {}
    public record Preview(long revision, Instant cutoff, Map<String,Long> eligible, int limitPerCategory) {}
    private record State(Config config, LocalDate scheduledDate, String status, Instant leaseUntil) {}
    private record Claim(UUID token, int retention) {}

    public View view() {
        return jdbc.queryForObject("SELECT * FROM data_cleanup_settings WHERE environment=?", (rs,n) -> {
            Map<String,Long> counts=new LinkedHashMap<>();
            var node=mapper.readTree(rs.getString("last_counts"));
            node.properties().forEach(entry -> counts.put(entry.getKey(),entry.getValue().asLong()));
            return new View(new Config(rs.getBoolean("enabled"),rs.getTime("daily_time").toLocalTime().toString(),
                rs.getInt("retention_days"),rs.getLong("revision")),"Asia/Kolkata",rs.getString("last_status"),
                instant(rs.getTimestamp("last_started_at")),instant(rs.getTimestamp("last_finished_at")),
                rs.getString("last_trigger"),rs.getString("last_error"),counts,LIMIT,
                rs.getTimestamp("lease_until")!=null && rs.getTimestamp("lease_until").toInstant().isAfter(clock.instant()));
        },scope());
    }
    private static Instant instant(Timestamp value) {return value==null?null:value.toInstant();}
    public View save(Config input,long actor) {
        LocalTime time;
        try {
            if (input.dailyTime()==null || !input.dailyTime().matches("\\d{2}:\\d{2}")) throw new IllegalArgumentException();
            time=LocalTime.parse(input.dailyTime());
        } catch (RuntimeException invalid) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use a valid daily time (HH:mm) in IST.");}
        if (input.retentionDays()<30 || input.retentionDays()>3650)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Retention must be between 30 and 3650 days.");
        int changed=jdbc.update("""
            UPDATE data_cleanup_settings SET enabled=?,daily_time=?,retention_days=?,revision=revision+1,
                updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE environment=? AND revision=?
            """,input.enabled(),java.sql.Time.valueOf(time),input.retentionDays(),actor,scope(),input.revision());
        if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Settings changed. Refresh before saving.");
        return view();
    }
    private Map<String,Object> parameters(int retention) {
        Instant now=clock.instant(),cutoff=now.minus(Duration.ofDays(retention));
        return Map.of("environment",scope(),"now",Timestamp.from(now),"cutoff",Timestamp.from(cutoff),
                "localCutoff",Timestamp.valueOf(LocalDateTime.ofInstant(cutoff,ApplicationClock.BUSINESS_ZONE)));
    }
    public Preview preview() {
        return tx.execute(status -> {
            deadlines();
            var config=view().config(); var params=parameters(config.retentionDays());
            // Count the bounded next run, rather than scanning an unbounded backlog.
            var customers=candidates(CUSTOMER,params,false); var staff=candidates(STAFF,params,false);
            Map<String,Long> counts=new LinkedHashMap<>();
            counts.put("customerNotifications",(long)customers.size());
            counts.put("customerDeliveries",children("customer_push_deliveries",customers));
            counts.put("staffAlerts",(long)staff.size());
            counts.put("staffReads",children("staff_order_alert_reads",staff));
            counts.put("staffDeliveries",children("staff_alert_deliveries",staff));
            return new Preview(config.revision(),((Timestamp)params.get("cutoff")).toInstant(),counts,LIMIT);
        });
    }
    private long children(String table,List<Long> ids) {
        if(ids.isEmpty())return 0;
        return named.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE event_id IN (:ids)",Map.of("ids",ids),Long.class);
    }
    private List<Long> candidates(String predicate,Map<String,Object> params,boolean lock) {
        return named.query("SELECT e.id "+predicate+" ORDER BY e.created_at,e.id LIMIT "+LIMIT+
            (lock?" FOR UPDATE OF e,o SKIP LOCKED":""),(params),(rs,n)->rs.getLong(1));
    }
    private State state(boolean lock) {
        var rows=jdbc.query("SELECT * FROM data_cleanup_settings WHERE environment=?"+(lock?" FOR UPDATE SKIP LOCKED":""),
            (rs,n)->new State(new Config(rs.getBoolean("enabled"),rs.getTime("daily_time").toLocalTime().toString(),
                rs.getInt("retention_days"),rs.getLong("revision")),rs.getObject("last_scheduled_date",LocalDate.class),
                rs.getString("last_status"),instant(rs.getTimestamp("lease_until"))),scope());
        return rows.isEmpty()?null:rows.getFirst();
    }
    static boolean due(Config config,LocalDate lastDate,String status,Instant leaseUntil,Instant now) {
        var local=now.atZone(ApplicationClock.BUSINESS_ZONE);
        return config.enabled() && !local.toLocalTime().isBefore(LocalTime.parse(config.dailyTime()))
            && (!local.toLocalDate().equals(lastDate) || ("RUNNING".equals(status) && leaseUntil!=null && !leaseUntil.isAfter(now)));
    }
    public void scheduledRun() {
        if(environment.getProperty("gokul.jobs.worker-enabled",Boolean.class,false))return;
        var state=state(false);
        if(state!=null && due(state.config(),state.scheduledDate(),state.status(),state.leaseUntil(),clock.instant()))run(null,null);
    }
    public View run(Long actor,Long expectedRevision) {
        Claim claim=tx.execute(status -> {
            deadlines(); var state=state(true); var now=clock.instant();
            if(state==null || (state.leaseUntil()!=null && state.leaseUntil().isAfter(now))) {
                if(actor==null)return null;
                throw new ResponseStatusException(HttpStatus.CONFLICT,"A cleanup is already running. Refresh for its result.");
            }
            if(actor==null && !due(state.config(),state.scheduledDate(),state.status(),state.leaseUntil(),now))return null;
            if(actor!=null && !Objects.equals(expectedRevision,state.config().revision()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Settings changed. Refresh and preview again.");
            UUID token=UUID.randomUUID();
            jdbc.update("""
                UPDATE data_cleanup_settings SET lease_token=?,lease_until=?,last_status='RUNNING',last_started_at=?,
                    last_finished_at=NULL,last_error=NULL,last_counts='{}',last_trigger=?,last_actor=?,last_run_token=?,
                    last_scheduled_date=CASE WHEN ? THEN ? ELSE last_scheduled_date END WHERE environment=?
                """,token,Timestamp.from(now.plusSeconds(300)),Timestamp.from(now),actor==null?"DAILY":"MANUAL",actor,token,
                actor==null,java.sql.Date.valueOf(now.atZone(ApplicationClock.BUSINESS_ZONE).toLocalDate()),scope());
            return new Claim(token,state.config().retentionDays());
        });
        if(claim==null)return view();
        try {
            return tx.execute(status -> {
                deadlines();
                // Hold the run row throughout deletion. An expired lease cannot be stolen mid-transaction.
                var tokens=jdbc.queryForList("SELECT lease_token FROM data_cleanup_settings WHERE environment=? FOR UPDATE",UUID.class,scope());
                if(tokens.isEmpty() || !claim.token().equals(tokens.getFirst()))throw new IllegalStateException("Cleanup lease changed.");
                var params=parameters(claim.retention());
                var customers=candidates(CUSTOMER,params,true); var staff=candidates(STAFF,params,true);
                Map<String,Long> counts=new LinkedHashMap<>();
                counts.put("customerDeliveries",delete("customer_push_deliveries","event_id",customers));
                counts.put("customerNotifications",delete("customer_notification_events","id",customers));
                counts.put("staffDeliveries",delete("staff_alert_deliveries","event_id",staff));
                counts.put("staffReads",delete("staff_order_alert_reads","event_id",staff));
                counts.put("staffAlerts",delete("staff_order_alerts","id",staff));
                jdbc.update("""
                    UPDATE data_cleanup_settings SET last_status='SUCCEEDED',last_finished_at=?,last_counts=?::jsonb,
                        lease_token=NULL,lease_until=NULL WHERE environment=? AND lease_token=?
                    """,Timestamp.from(clock.instant()),mapper.writeValueAsString(counts),scope(),claim.token());
                // Capture this run before releasing the lock; another run may claim immediately after commit.
                return view();
            });
        } catch(RuntimeException failure) {
            log.warn("Cleanup execution/commit raised an error for {}; reconciling its persisted result",scope(),failure);
            View reconciled;
            try {
                reconciled=tx.execute(status -> {
                    deadlines();
                    var rows=jdbc.queryForList("SELECT last_run_token,lease_token,last_status FROM data_cleanup_settings WHERE environment=? FOR UPDATE",scope());
                    if(rows.isEmpty() || !claim.token().equals(rows.getFirst().get("last_run_token")))return null;
                    // Success and deleted counts committed atomically. A lost acknowledgement is still success.
                    if("SUCCEEDED".equals(rows.getFirst().get("last_status")))return view();
                    // Acquiring this row lock waits for any uncertain original transaction to finish.
                    // Its original claim still being RUNNING proves the delete transaction did not commit.
                    if(!"RUNNING".equals(rows.getFirst().get("last_status"))
                            || !claim.token().equals(rows.getFirst().get("lease_token")))return null;
                    jdbc.update("""
                        UPDATE data_cleanup_settings SET last_status='FAILED',last_finished_at=?,
                            last_error='Cleanup failed; no records were deleted. Review server logs and retry.',
                            lease_token=NULL,lease_until=NULL WHERE environment=? AND lease_token=?
                        """,Timestamp.from(clock.instant()),scope(),claim.token());
                    return view();
                });
            } catch(RuntimeException reconciliationFailure) {
                log.warn("Cleanup result could not be confirmed for {}",scope(),reconciliationFailure);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cleanup result could not be confirmed. Refresh the last run before trying again.");
            }
            if(reconciled==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Cleanup result could not be confirmed. Refresh the last run before trying again.");
            if("SUCCEEDED".equals(reconciled.status()))return reconciled;
            if(actor!=null)throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Cleanup failed; no records were deleted. Refresh for details.");
            return reconciled;
        }
    }
    private long delete(String table,String column,List<Long> ids) {
        return ids.isEmpty()?0:named.update("DELETE FROM "+table+" WHERE "+column+" IN (:ids)",Map.of("ids",ids));
    }
    private void deadlines() {
        jdbc.execute("SET LOCAL statement_timeout='10s'");
        jdbc.execute("SET LOCAL lock_timeout='2s'");
    }
}
