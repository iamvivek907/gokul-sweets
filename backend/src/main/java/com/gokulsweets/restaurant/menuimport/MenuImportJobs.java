package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.*;
import com.gokulsweets.restaurant.staff.*;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.security.*;
import java.util.*;

/** Backend menu import jobs contract and implementation. */
@Service
@RequiredArgsConstructor
public class MenuImportJobs {

    private final JdbcTemplate jdbc;

    private final StaffAuthorizationService authorization;

    @Value("${gokul.imports.async-enabled:false}")
    private boolean enabled;

    /**
     * Enableds the operation.
     *
     * @return the enabled result
     */
    public boolean enabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportJobs.class, "enabled()");
        try {
            return enabled;
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MenuImportJobs.class, "enabled()");
        }
    }

    /**
     * Immutable job data contract.
     *
     * @param id the id
     * @param operation the operation
     * @param status the status
     * @param result the result
     * @param error the error
     */
    public record Job(UUID id, String operation, String status, String result, String error) {}

    /**
     * Authorizes the operation.
     *
     * @param branchId the branch id
     * @return the authorize result
     */
    private long authorize(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportJobs.class, "authorize(long)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            authorization.requireBranchAccess(branchId);
            return authorization.getCurrentStaff().getId();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MenuImportJobs.class, "authorize(long)");
        }
    }

    /**
     * Enqueues the operation.
     *
     * @param branchId the branch id
     * @param file the file
     * @param operation the operation
     * @return the enqueue result
     */
    @Transactional
    public Job enqueue(long branchId, MultipartFile file, String operation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportJobs.class, "enqueue(long,MultipartFile,String)");
        try {
            return enqueue(branchId, file, operation, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuImportJobs.class,
                    "enqueue(long,MultipartFile,String)");
        }
    }

    /**
     * Enqueues the operation.
     *
     * @param branchId the branch id
     * @param file the file
     * @param operation the operation
     * @param submissionId the submission id
     * @return the enqueue result
     */
    @Transactional
    public Job enqueue(long branchId, MultipartFile file, String operation, UUID submissionId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportJobs.class, "enqueue(long,MultipartFile,String,UUID)");
        try {
            long staff = authorize(branchId);
            if (!Set.of("VALIDATE", "IMPORT").contains(operation))
                throw new IllegalArgumentException("Invalid menu operation.");
            if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024)
                throw new IllegalArgumentException("Choose an Excel file of at most 2 MB.");
            if (!Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM branches WHERE id=?)",
                            Boolean.class,
                            branchId))) throw new IllegalArgumentException("Branch not found.");
            // Serialize admission only, not processing. Bound queued binary data across replicas.
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(714114)", Object.class);
            byte[] bytes;
            String digest;
            try {
                bytes = file.getBytes();
                digest =
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("Unable to read the upload.");
            }
            if (submissionId != null) {
                var prior =
                        jdbc.query(
                                "SELECT j.* FROM menu_import_submissions s JOIN menu_import_jobs j"
                                        + " ON j.id=s.job_id WHERE s.id=?",
                                (r, n) ->
                                        new Object[] {
                                            job(r),
                                            r.getLong("branch_id"),
                                            r.getLong("staff_id"),
                                            r.getString("file_digest")
                                        },
                                submissionId);
                if (!prior.isEmpty()) {
                    var value = prior.getFirst();
                    var existing = (Job) value[0];
                    if (value[1].equals(branchId)
                            && value[2].equals(staff)
                            && value[3].equals(digest)
                            && existing.operation().equals(operation)) return existing;
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "This upload identifier belongs to a different submission.");
                }
            }
            var active =
                    jdbc.query(
                            "SELECT id,operation,status,result,error,file_digest,staff_id FROM"
                                    + " menu_import_jobs WHERE branch_id=? AND status IN"
                                    + " ('QUEUED','PROCESSING')",
                            (r, n) ->
                                    new Object[] {
                                        job(r), r.getString("file_digest"), r.getLong("staff_id")
                                    },
                            branchId);
            if (!active.isEmpty()) {
                var value = active.getFirst();
                var job = (Job) value[0];
                if (value[1].equals(digest)
                        && value[2].equals(staff)
                        && job.operation().equals(operation))
                    return linkSubmission(submissionId, job);
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A menu job is already running for this branch. Wait for it to finish.");
            }
            if (jdbc.queryForObject(
                            "SELECT COUNT(*) FROM menu_import_jobs WHERE status IN"
                                    + " ('QUEUED','PROCESSING')",
                            Long.class)
                    >= 10)
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "The import queue is full. Please try later.");
            UUID id = UUID.randomUUID();
            String name = Optional.ofNullable(file.getOriginalFilename()).orElse("menu.xlsx");
            name = name.substring(0, Math.min(255, name.length()));
            jdbc.update(
                    "INSERT INTO"
                        + " menu_import_jobs(id,branch_id,staff_id,operation,filename,file_digest,payload)"
                        + " VALUES(?,?,?,?,?,?,?)",
                    id,
                    branchId,
                    staff,
                    operation,
                    name,
                    digest,
                    bytes);
            return linkSubmission(submissionId, new Job(id, operation, "QUEUED", null, null));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuImportJobs.class,
                    "enqueue(long,MultipartFile,String,UUID)");
        }
    }

    /**
     * Links submission.
     *
     * @param id the id
     * @param job the job
     * @return the link submission result
     */
    private Job linkSubmission(UUID id, Job job) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportJobs.class, "linkSubmission(UUID,Job)");
        try {
            if (id != null)
                jdbc.update(
                        "INSERT INTO menu_import_submissions(id,job_id) VALUES(?,?)", id, job.id());
            return job;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuImportJobs.class, "linkSubmission(UUID,Job)");
        }
    }

    /**
     * Returns submission.
     *
     * @param branchId the branch id
     * @param submissionId the submission id
     * @return the get submission result
     */
    @Transactional(readOnly = true)
    public Job getSubmission(long branchId, UUID submissionId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportJobs.class, "getSubmission(long,UUID)");
        try {
            long staff = authorize(branchId);
            var matches =
                    jdbc.query(
                            "SELECT j.* FROM menu_import_submissions s JOIN menu_import_jobs j ON"
                                + " j.id=s.job_id WHERE s.id=? AND j.branch_id=? AND j.staff_id=?",
                            (r, n) -> job(r),
                            submissionId,
                            branchId,
                            staff);
            if (!matches.isEmpty()) return matches.getFirst();
            if (!enabled)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This backend uses synchronous uploads; their status cannot be recovered."
                                + " Check the menu before uploading again.");
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "The upload has not been acknowledged yet. Check its status again before"
                            + " uploading another file.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuImportJobs.class, "getSubmission(long,UUID)");
        }
    }

    /**
     * Returns the operation.
     *
     * @param branchId the branch id
     * @param id the id
     * @return the get result
     */
    @Transactional(readOnly = true)
    public Job get(long branchId, UUID id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportJobs.class, "get(long,UUID)");
        try {
            authorize(branchId);
            return jdbc
                    .query(
                            "SELECT id,operation,status,result,error FROM menu_import_jobs WHERE"
                                    + " id=? AND branch_id=?",
                            (r, n) -> job(r),
                            id,
                            branchId)
                    .stream()
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND, "Menu job not found."));
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MenuImportJobs.class, "get(long,UUID)");
        }
    }

    /**
     * Jobs the operation.
     *
     * @param r the r
     * @return the job result
     * @throws java.sql.SQLException if the operation cannot complete
     */
    static Job job(java.sql.ResultSet r) throws java.sql.SQLException {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportJobs.class, "job(java.sql.ResultSet)");
        try {
            return new Job(
                    r.getObject("id", UUID.class),
                    r.getString("operation"),
                    r.getString("status"),
                    r.getString("result"),
                    r.getString("error"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuImportJobs.class, "job(java.sql.ResultSet)");
        }
    }
}
