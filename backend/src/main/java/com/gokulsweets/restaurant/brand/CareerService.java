package com.gokulsweets.restaurant.brand;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

/** Coordinates career operations. */
@Service
@RequiredArgsConstructor
public class CareerService {

    private final JdbcTemplate jdbc;

    private final StaffAuthorizationService staff;

    /** The statuses value. */
    public static final Set<String> STATUSES =
            Set.of("NEW", "REVIEWING", "SHORTLISTED", "INTERVIEW", "HIRED", "REJECTED");

    /**
     * Immutable job data contract.
     *
     * @param id the id
     * @param branchId the branch id
     * @param branchName the branch name
     * @param title the title
     * @param department the department
     * @param requirements the requirements
     * @param minimumExperience the minimum experience
     * @param active the active
     * @param version the version
     */
    public record Job(
            long id,
            long branchId,
            String branchName,
            String title,
            String department,
            String requirements,
            BigDecimal minimumExperience,
            boolean active,
            long version) {}

    /**
     * Immutable job input data contract.
     *
     * @param branchId the branch id
     * @param title the title
     * @param department the department
     * @param requirements the requirements
     * @param minimumExperience the minimum experience
     * @param active the active
     */
    public record JobInput(
            @Positive long branchId,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 80) String department,
            @NotBlank @Size(max = 3000) String requirements,
            @NotNull @DecimalMin("0") @DecimalMax("60") @Digits(integer = 2, fraction = 1)
                    BigDecimal minimumExperience,
            boolean active) {}

    /**
     * Immutable application input data contract.
     *
     * @param requestId the request id
     * @param jobId the job id
     * @param branchId the branch id
     * @param name the name
     * @param phone the phone
     * @param email the email
     * @param desiredRole the desired role
     * @param experience the experience
     * @param qualifications the qualifications
     * @param consent the consent
     * @param website the website
     */
    public record ApplicationInput(
            @NotNull UUID requestId,
            Long jobId,
            @Positive long branchId,
            @NotBlank @Size(min = 2, max = 100) String name,
            @NotBlank @Pattern(regexp = "(?:\\+91)?[6-9][0-9]{9}") String phone,
            @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 120) String desiredRole,
            @NotNull @DecimalMin("0") @DecimalMax("60") @Digits(integer = 2, fraction = 1)
                    BigDecimal experience,
            @NotBlank @Size(max = 2000) String qualifications,
            @AssertTrue boolean consent,
            @Size(max = 0) String website) {}

    /**
     * Immutable receipt data contract.
     *
     * @param reference the reference
     * @param message the message
     */
    public record Receipt(UUID reference, String message) {}

    /**
     * Immutable applicant data contract.
     *
     * @param id the id
     * @param jobId the job id
     * @param branchId the branch id
     * @param branchName the branch name
     * @param jobTitle the job title
     * @param name the name
     * @param phone the phone
     * @param email the email
     * @param desiredRole the desired role
     * @param experience the experience
     * @param qualifications the qualifications
     * @param status the status
     * @param staffNotes the staff notes
     * @param createdAt the created at
     * @param version the version
     */
    public record Applicant(
            UUID id,
            Long jobId,
            long branchId,
            String branchName,
            String jobTitle,
            String name,
            String phone,
            String email,
            String desiredRole,
            BigDecimal experience,
            String qualifications,
            String status,
            String staffNotes,
            OffsetDateTime createdAt,
            long version) {}

    /**
     * Immutable page data contract.
     *
     * @param items the items
     * @param total the total
     * @param page the page
     * @param size the size
     * @param counts the counts
     */
    public record Page(
            List<Applicant> items, long total, int page, int size, Map<String, Long> counts) {}

    /**
     * Immutable update data contract.
     *
     * @param status the status
     * @param staffNotes the staff notes
     */
    public record Update(
            @Pattern(regexp = "NEW|REVIEWING|SHORTLISTED|INTERVIEW|HIRED|REJECTED") @NotNull
                    String status,
            @NotNull @Size(max = 3000) String staffNotes) {}

    /**
     * Returns job information for career.
     *
     * @param r the r supplied to this method
     * @param n the n supplied to this method
     * @return the {@code Job} result
     * @throws SQLException if the underlying operation fails
     */
    private Job job(ResultSet r, int n) throws SQLException {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "job(ResultSet,int)");
        try {
            return new Job(
                    r.getLong("id"),
                    r.getLong("branch_id"),
                    r.getString("branch_name"),
                    r.getString("title"),
                    r.getString("department"),
                    r.getString("requirements"),
                    r.getBigDecimal("minimum_experience"),
                    r.getBoolean("active"),
                    r.getLong("version"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CareerService.class, "job(ResultSet,int)");
        }
    }

    /**
     * Returns applicant information for career.
     *
     * @param r the r supplied to this method
     * @param n the n supplied to this method
     * @return the {@code Applicant} result
     * @throws SQLException if the underlying operation fails
     */
    private Applicant applicant(ResultSet r, int n) throws SQLException {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "applicant(ResultSet,int)");
        try {
            return new Applicant(
                    r.getObject("id", UUID.class),
                    r.getObject("job_id", Long.class),
                    r.getLong("branch_id"),
                    r.getString("branch_name"),
                    r.getString("job_title"),
                    r.getString("name"),
                    r.getString("phone"),
                    r.getString("email"),
                    r.getString("desired_role"),
                    r.getBigDecimal("experience"),
                    r.getString("qualifications"),
                    r.getString("status"),
                    r.getString("staff_notes"),
                    r.getObject("created_at", OffsetDateTime.class),
                    r.getLong("version"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CareerService.class, "applicant(ResultSet,int)");
        }
    }

    private static final String JOBS = AppConstant.CAREER_SERVICE_JOBS;

    private static final String APPLICANTS = AppConstant.CAREER_SERVICE_APPLICANTS;

    /**
     * Returns check information for career.
     *
     * <p>Authorization checks include {@code PermissionName.CAREERS_MANAGE}.
     */
    private void check() {
        final long __gokulMethodStartedNanos = MethodTiming.start(CareerService.class, "check()");
        try {
            staff.requirePermission(PermissionName.CAREERS_MANAGE);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CareerService.class, "check()");
        }
    }

    /**
     * Records an audit entry for career data.
     *
     * <p>Writes {@code brand_career_audit}.
     *
     * @param action the action supplied to this method
     * @param id the id supplied to this method
     */
    private void audit(String action, String id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "audit(String,String)");
        try {
            jdbc.update(
                    "INSERT INTO brand_career_audit(staff_id,action,entity_id) VALUES(?,?,?)",
                    staff.getCurrentStaff().getId(),
                    action,
                    id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CareerService.class, "audit(String,String)");
        }
    }

    /**
     * Returns visible information for career.
     *
     * @param alias the alias supplied to this method
     * @param params the params supplied to this method
     * @param requested the requested supplied to this method
     * @return the {@code String} result
     */
    private String visible(String alias, List<Object> params, Long requested) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "visible(String,List<Object>,Long)");
        try {
            if (requested != null) {
                staff.requireBranchAccess(requested);
                params.add(requested);
                return " AND " + alias + ".branch_id=?";
            }
            var current = staff.getCurrentStaff();
            if (current.getRole().getName().equals("OWNER_ADMIN")) return "";
            var ids = current.getBranches().stream().map(b -> b.getId()).toList();
            if (ids.isEmpty()) return " AND FALSE";
            params.addAll(ids);
            return " AND "
                    + alias
                    + ".branch_id IN ("
                    + String.join(",", Collections.nCopies(ids.size(), "?"))
                    + ")";
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CareerService.class,
                    "visible(String,List<Object>,Long)");
        }
    }

    /**
     * Publics jobs.
     *
     * @param branch the branch
     * @return the public jobs result
     */
    @Transactional(readOnly = true)
    public List<Job> publicJobs(Long branch) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "publicJobs(Long)");
        try {
            var args = new ArrayList<Object>();
            String where = " WHERE j.active AND b.active";
            if (branch != null) {
                where += " AND j.branch_id=?";
                args.add(branch);
            }
            return jdbc.query(
                    JOBS + where + " ORDER BY j.id DESC LIMIT 100", this::job, args.toArray());
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CareerService.class, "publicJobs(Long)");
        }
    }

    /**
     * Returns jobs information for career.
     *
     * @param branch the branch supplied to this method
     * @return the {@code List<Job>} result
     */
    @Transactional(readOnly = true)
    public List<Job> jobs(Long branch) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "jobs(Long)");
        try {
            check();
            var args = new ArrayList<Object>();
            return jdbc.query(
                    JOBS
                            + " WHERE TRUE"
                            + visible("j", args, branch)
                            + " ORDER BY j.id DESC LIMIT 200",
                    this::job,
                    args.toArray());
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CareerService.class, "jobs(Long)");
        }
    }

    /**
     * Creates career data and returns the {@code Job} result.
     *
     * <p>Writes {@code career_jobs}.
     *
     * @param input the input supplied to this method
     * @return the value of {@code jobById(id)}
     */
    @Transactional
    public Job create(JobInput input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "create(JobInput)");
        try {
            check();
            staff.requireBranchAccess(input.branchId());
            Long id =
                    jdbc.queryForObject(
                            "INSERT INTO"
                                + " career_jobs(branch_id,title,department,requirements,minimum_experience,active)"
                                + " VALUES(?,?,?,?,?,?) RETURNING id",
                            Long.class,
                            input.branchId(),
                            input.title().trim(),
                            input.department().trim(),
                            input.requirements().trim(),
                            input.minimumExperience(),
                            input.active());
            audit("JOB_CREATE", String.valueOf(id));
            return jobById(id);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CareerService.class, "create(JobInput)");
        }
    }

    /**
     * Jobs by id.
     *
     * @param id the id
     * @return the job by id result
     */
    private Job jobById(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "jobById(long)");
        try {
            return jdbc.query(JOBS + " WHERE j.id=?", this::job, id).stream()
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND, "Job not found."));
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CareerService.class, "jobById(long)");
        }
    }

    /**
     * Persists career data and returns the {@code Job} result.
     *
     * <p>Writes {@code career_jobs}.
     *
     * @param id the id supplied to this method
     * @param input the input supplied to this method
     * @param version the version supplied to this method
     * @return the value of {@code jobById(id)}
     * @throws IllegalArgumentException when the method rejects the request with {@code Create a new
     *     role for a different branch so existing applications stay correctly linked.}
     * @throws ResponseStatusException when the method rejects the request with {@code Job changed.
     *     Reload before saving.}
     */
    @Transactional
    public Job save(long id, JobInput input, long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "save(long,JobInput,long)");
        try {
            check();
            var before = jobById(id);
            staff.requireBranchAccess(before.branchId());
            staff.requireBranchAccess(input.branchId());
            if (before.branchId() != input.branchId())
                throw new IllegalArgumentException(
                        "Create a new role for a different branch so existing applications stay"
                                + " correctly linked.");
            int n =
                    jdbc.update(
                            "UPDATE career_jobs SET"
                                + " title=?,department=?,requirements=?,minimum_experience=?,active=?,version=version+1"
                                + " WHERE id=? AND version=?",
                            input.title().trim(),
                            input.department().trim(),
                            input.requirements().trim(),
                            input.minimumExperience(),
                            input.active(),
                            id,
                            version);
            if (n != 1)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Job changed. Reload before saving.");
            audit("JOB_SAVE", String.valueOf(id));
            return jobById(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CareerService.class, "save(long,JobInput,long)");
        }
    }

    /**
     * Clients key.
     *
     * @param client the client
     * @return the client key result
     */
    private String clientKey(String client) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "clientKey(String)");
        try {
            try {
                return HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(client.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                throw new IllegalStateException("Could not check request limit.", e);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CareerService.class, "clientKey(String)");
        }
    }

    /**
     * Applies career data and returns the {@code Receipt} result.
     *
     * <p>Reads {@code branches}, {@code career_applications}, {@code career_jobs}.
     *
     * <p>Writes {@code career_application_throttle}, {@code career_applications}.
     *
     * @param input the input supplied to this method
     * @param client the client supplied to this method
     * @return the {@code Receipt} result
     * @throws IllegalArgumentException when the method rejects the request with {@code Choose an
     *     active branch.}
     * @throws ResponseStatusException when the method rejects the request with {@code An
     *     application for this role and contact is already under review. No duplicate application
     *     was created.}; {@code This role is closed. Choose another opening or send general
     *     interest.}; {@code This submission reference was already used. Start a new application.};
     *     {@code Too many applications from this connection. Please try later.}
     */
    @Transactional
    public Receipt apply(ApplicationInput input, String client) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "apply(ApplicationInput,String)");
        try {
            String phone = input.phone().replace("+91", "");
            var previous =
                    jdbc.query(
                            "SELECT phone,branch_id,job_id FROM career_applications WHERE id=?",
                            (r, n) ->
                                    List.of(
                                            r.getString("phone"),
                                            String.valueOf(r.getLong("branch_id")),
                                            String.valueOf(r.getObject("job_id"))),
                            input.requestId());
            if (!previous.isEmpty()) {
                var p = previous.getFirst();
                if (!p.get(0).equals(phone)
                        || !p.get(1).equals(String.valueOf(input.branchId()))
                        || !p.get(2).equals(String.valueOf(input.jobId())))
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "This submission reference was already used. Start a new application.");
                return receipt(input.requestId());
            }
            // Atomic, shared database throttle. Never log applicant contacts or proxy-supplied
            // headers.
            jdbc.update(
                    "DELETE FROM career_application_throttle WHERE"
                            + " window_start<CURRENT_TIMESTAMP-INTERVAL '2 hours'");
            Integer attempts =
                    jdbc.queryForObject(
                            """
INSERT INTO career_application_throttle(client_key,requests) VALUES(?,1)
ON CONFLICT(client_key) DO UPDATE SET requests=CASE WHEN career_application_throttle.window_start<CURRENT_TIMESTAMP-INTERVAL '1 hour' THEN 1 ELSE career_application_throttle.requests+1 END,
 window_start=CASE WHEN career_application_throttle.window_start<CURRENT_TIMESTAMP-INTERVAL '1 hour' THEN CURRENT_TIMESTAMP ELSE career_application_throttle.window_start END RETURNING requests
""",
                            Integer.class,
                            clientKey(client));
            if (attempts > 20)
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many applications from this connection. Please try later.");
            if (!Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM branches WHERE id=? AND active)",
                            Boolean.class,
                            input.branchId())))
                throw new IllegalArgumentException("Choose an active branch.");
            String role = input.desiredRole().trim();
            if (input.jobId() != null) {
                var rows =
                        jdbc.query(
                                "SELECT id FROM career_jobs WHERE id=? AND branch_id=? AND active"
                                        + " FOR SHARE",
                                (r, n) -> r.getLong(1),
                                input.jobId(),
                                input.branchId());
                if (rows.isEmpty())
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "This role is closed. Choose another opening or send general"
                                    + " interest.");
                role = jobById(input.jobId()).title();
            }
            int inserted =
                    jdbc.update(
                            "INSERT INTO"
                                + " career_applications(id,job_id,branch_id,name,phone,email,desired_role,experience,qualifications,consent_version)"
                                + " VALUES(?,?,?,?,?,?,?,?,?,'CAREERS-1') ON CONFLICT DO NOTHING",
                            input.requestId(),
                            input.jobId(),
                            input.branchId(),
                            input.name().trim(),
                            phone,
                            input.email() == null || input.email().isBlank()
                                    ? null
                                    : input.email().trim(),
                            role,
                            input.experience(),
                            input.qualifications().trim());
            if (inserted == 0) {
                if (Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM career_applications WHERE id=?)",
                                Boolean.class,
                                input.requestId()))) return receipt(input.requestId());
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "An application for this role and contact is already under review. No"
                                + " duplicate application was created.");
            }
            return receipt(input.requestId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CareerService.class,
                    "apply(ApplicationInput,String)");
        }
    }

    /**
     * Returns receipt information for career.
     *
     * @param id the id supplied to this method
     * @return the {@code Receipt} result
     */
    private Receipt receipt(UUID id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "receipt(UUID)");
        try {
            return new Receipt(
                    id,
                    "Application received. Our team will contact you if there is a suitable"
                            + " opportunity.");
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CareerService.class, "receipt(UUID)");
        }
    }

    /**
     * Returns applicants information for career.
     *
     * @param branch the branch supplied to this method
     * @param job the job supplied to this method
     * @param status the status supplied to this method
     * @param search the search supplied to this method
     * @param minimum the minimum supplied to this method
     * @param maximum the maximum supplied to this method
     * @param page the page supplied to this method
     * @param size the size supplied to this method
     * @return the {@code Page} result
     * @throws IllegalArgumentException when the method rejects the request with {@code Choose valid
     *     applicant filters.}; {@code Unknown application status.}
     */
    @Transactional
    public Page applicants(
            Long branch,
            Long job,
            String status,
            String search,
            BigDecimal minimum,
            BigDecimal maximum,
            int page,
            int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CareerService.class,
                        "applicants(Long,Long,String,String,BigDecimal,BigDecimal,int,int)");
        try {
            check();
            if (page < 0
                    || page > 10000
                    || size < 1
                    || size > 100
                    || search != null && search.length() > 100
                    || minimum != null && minimum.signum() < 0
                    || maximum != null && maximum.compareTo(BigDecimal.valueOf(60)) > 0
                    || minimum != null && maximum != null && minimum.compareTo(maximum) > 0)
                throw new IllegalArgumentException("Choose valid applicant filters.");
            var args = new ArrayList<Object>();
            String where = " WHERE TRUE" + visible("a", args, branch);
            if (job != null) {
                where += " AND a.job_id=?";
                args.add(job);
            }
            if (minimum != null) {
                where += " AND a.experience>=?";
                args.add(minimum);
            }
            if (maximum != null) {
                where += " AND a.experience<=?";
                args.add(maximum);
            }
            if (search != null && !search.isBlank()) {
                where +=
                        " AND to_tsvector('simple',a.name||' '||a.desired_role||'"
                                + " '||a.qualifications) @@ plainto_tsquery('simple',?)";
                args.add(search.trim());
            }
            var counts = new LinkedHashMap<String, Long>();
            jdbc.query(
                    "SELECT a.status,COUNT(*) n FROM career_applications a"
                            + where
                            + " GROUP BY a.status",
                    (RowCallbackHandler) r -> counts.put(r.getString(1), r.getLong(2)),
                    args.toArray());
            if (status != null && !status.isBlank()) {
                if (!STATUSES.contains(status))
                    throw new IllegalArgumentException("Unknown application status.");
                where += " AND a.status=?";
                args.add(status);
            }
            Long total =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM career_applications a" + where,
                            Long.class,
                            args.toArray());
            var paged = new ArrayList<>(args);
            paged.add(size);
            paged.add((long) page * size);
            var items =
                    jdbc.query(
                            APPLICANTS
                                    + where
                                    + " ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",
                            this::applicant,
                            paged.toArray());
            audit("APPLICANTS_VIEW", "page-" + page);
            return new Page(items, total, page, size, counts);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CareerService.class,
                    "applicants(Long,Long,String,String,BigDecimal,BigDecimal,int,int)");
        }
    }

    /**
     * Returns detail information for career.
     *
     * @param id the id supplied to this method
     * @return the value of {@code applicant}
     */
    @Transactional
    public Applicant detail(UUID id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "detail(UUID)");
        try {
            check();
            var applicant =
                    jdbc.query(APPLICANTS + " WHERE a.id=?", this::applicant, id).stream()
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND,
                                                    "Application not found."));
            staff.requireBranchAccess(applicant.branchId());
            audit("APPLICATION_VIEW", id.toString());
            return applicant;
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CareerService.class, "detail(UUID)");
        }
    }

    /**
     * Updates career data and returns the {@code Applicant} result.
     *
     * <p>Writes {@code career_applications}.
     *
     * @param id the id supplied to this method
     * @param input the input supplied to this method
     * @param version the version supplied to this method
     * @return the {@code Applicant} result
     * @throws ResponseStatusException when the method rejects the request with {@code Application
     *     changed. Reload before saving.}
     */
    @Transactional
    public Applicant update(UUID id, Update input, long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerService.class, "update(UUID,Update,long)");
        try {
            check();
            var current =
                    jdbc.query(APPLICANTS + " WHERE a.id=?", this::applicant, id).stream()
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND,
                                                    "Application not found."));
            staff.requireBranchAccess(current.branchId());
            int n =
                    jdbc.update(
                            "UPDATE career_applications SET"
                                + " status=?,staff_notes=?,version=version+1,updated_at=CURRENT_TIMESTAMP"
                                + " WHERE id=? AND version=?",
                            input.status(),
                            input.staffNotes().trim(),
                            id,
                            version);
            if (n != 1)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Application changed. Reload before saving.");
            audit("APPLICATION_" + input.status(), id.toString());
            return jdbc.queryForObject(APPLICANTS + " WHERE a.id=?", this::applicant, id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CareerService.class, "update(UUID,Update,long)");
        }
    }
}
