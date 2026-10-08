package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffUserDetailsService;
import com.gokulsweets.restaurant.staff.StaffUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** Backend menu import worker contract and implementation. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "gokul.jobs.worker-enabled", havingValue = "true")
public class MenuImportWorker {

    private final JdbcTemplate jdbc;

    private final PlatformTransactionManager manager;

    private final MenuImportService imports;

    private final StaffUserRepository staff;

    private final StaffUserDetailsService users;

    private final ObjectMapper mapper;

    /** Immutable claim data contract. */
    record Claim(UUID id, UUID token) {}

    /** Processes the operation. */
    @Scheduled(
            fixedDelayString = "${gokul.jobs.poll-ms:2000}",
            initialDelayString = "${gokul.jobs.poll-ms:2000}")
    public void process() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportWorker.class, "process()");
        try {
            var transaction = new TransactionTemplate(manager);
            var claim =
                    transaction.execute(
                            status -> {
                                var ids =
                                        jdbc.queryForList(
                                                "SELECT id FROM menu_import_jobs WHERE"
                                                    + " status='QUEUED' OR (status='PROCESSING' AND"
                                                    + " lease_until<CURRENT_TIMESTAMP) ORDER BY"
                                                    + " created_at FOR UPDATE SKIP LOCKED LIMIT 1",
                                                UUID.class);
                                if (ids.isEmpty()) return null;
                                var id = ids.getFirst();
                                var token = UUID.randomUUID();
                                int attempts =
                                        jdbc.queryForObject(
                                                "SELECT attempts FROM menu_import_jobs WHERE id=?",
                                                Integer.class,
                                                id);
                                if (attempts >= 3) {
                                    jdbc.update(
                                            "UPDATE menu_import_jobs SET"
                                                + " status='FAILED',error='Worker stopped"
                                                + " repeatedly. Upload again after checking worker"
                                                + " health.',payload=NULL,updated_at=CURRENT_TIMESTAMP"
                                                + " WHERE id=?",
                                            id);
                                    return null;
                                }
                                jdbc.update(
                                        "UPDATE menu_import_jobs SET"
                                            + " status='PROCESSING',attempts=attempts+1,claim_token=?,lease_until=CURRENT_TIMESTAMP+INTERVAL"
                                            + " '10 minutes',updated_at=CURRENT_TIMESTAMP WHERE"
                                            + " id=?",
                                        token,
                                        id);
                                return new Claim(id, token);
                            });
            if (claim == null) return;
            var previous = SecurityContextHolder.getContext();
            try {
                transaction.executeWithoutResult(
                        status -> {
                            // Lock is held through the import and its result: process death rolls
                            // both back.
                            var rows =
                                    jdbc.queryForList(
                                            "SELECT * FROM menu_import_jobs WHERE id=? AND"
                                                    + " claim_token=? AND status='PROCESSING' FOR"
                                                    + " UPDATE",
                                            claim.id(),
                                            claim.token());
                            if (rows.isEmpty()) return;
                            var row = rows.getFirst();
                            var owner =
                                    staff.findDetailedById(
                                                    ((Number) row.get("staff_id")).longValue())
                                            .orElseThrow();
                            var details = users.loadUserByUsername(owner.getUsername());
                            if (!details.isEnabled())
                                throw new org.springframework.security.access.AccessDeniedException(
                                        "Import requester is disabled.");
                            var context = SecurityContextHolder.createEmptyContext();
                            context.setAuthentication(
                                    UsernamePasswordAuthenticationToken.authenticated(
                                            details, null, details.getAuthorities()));
                            SecurityContextHolder.setContext(context);
                            var file =
                                    new Upload(
                                            (String) row.get("filename"),
                                            (byte[]) row.get("payload"));
                            long branch = ((Number) row.get("branch_id")).longValue();
                            if (details.getAuthorities().stream()
                                            .noneMatch(a -> a.getAuthority().equals("MENU_MANAGE"))
                                    || !owner.getRole().getName().equals("OWNER_ADMIN")
                                            && owner.getBranches().stream()
                                                    .noneMatch(b -> b.getId() == branch))
                                throw new org.springframework.security.access.AccessDeniedException(
                                        "Import requester no longer has branch menu access.");
                            Object result =
                                    row.get("operation").equals("VALIDATE")
                                            ? imports.validate(branch, file)
                                            : imports.importMenu(branch, file);
                            jdbc.update(
                                    "UPDATE menu_import_jobs SET"
                                        + " status='SUCCEEDED',result=?,payload=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP"
                                        + " WHERE id=?",
                                    mapper.writeValueAsString(result),
                                    claim.id());
                        });
            } catch (Exception failure) {
                log.warn("Menu job failed: jobId={}", claim.id(), failure);
                transaction.executeWithoutResult(
                        status ->
                                jdbc.update(
                                        "UPDATE menu_import_jobs SET"
                                            + " status='FAILED',error=?,payload=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP"
                                            + " WHERE id=? AND claim_token=? AND"
                                            + " status='PROCESSING'",
                                        safeFailureMessage(failure),
                                        claim.id(),
                                        claim.token()));
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MenuImportWorker.class, "process()");
        }
    }

    /**
     * Safes failure message.
     *
     * @param failure the failure
     * @return the safe failure message result
     */
    static String safeFailureMessage(Exception failure) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportWorker.class, "safeFailureMessage(Exception)");
        try {
            // Only application-authored validation errors may be shown to staff.
            // Database, authorization and unexpected parser failures remain generic.
            String message = failure.getMessage();
            if (failure instanceof MenuImportValidationException && message != null)
                return message.substring(0, Math.min(message.length(), 500));
            return "Menu job failed. Check the file and your current branch permissions, then try"
                    + " again.";
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuImportWorker.class,
                    "safeFailureMessage(Exception)");
        }
    }

    /** Immutable upload data contract. */
    record Upload(String filename, byte[] bytes) implements MultipartFile {

        /**
         * Returns name.
         *
         * @return the get name result
         */
        public String getName() {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuImportWorker.Upload.class, "getName()");
            try {
                return "file";
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos, MenuImportWorker.Upload.class, "getName()");
            }
        }

        /**
         * Returns original filename.
         *
         * @return the get original filename result
         */
        public String getOriginalFilename() {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuImportWorker.Upload.class, "getOriginalFilename()");
            try {
                return filename;
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        MenuImportWorker.Upload.class,
                        "getOriginalFilename()");
            }
        }

        /**
         * Returns content type.
         *
         * @return the get content type result
         */
        public String getContentType() {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuImportWorker.Upload.class, "getContentType()");
            try {
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        MenuImportWorker.Upload.class,
                        "getContentType()");
            }
        }

        /**
         * Reports whether empty.
         *
         * @return the is empty result
         */
        public boolean isEmpty() {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuImportWorker.Upload.class, "isEmpty()");
            try {
                return bytes.length == 0;
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos, MenuImportWorker.Upload.class, "isEmpty()");
            }
        }

        /**
         * Returns size.
         *
         * @return the get size result
         */
        public long getSize() {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuImportWorker.Upload.class, "getSize()");
            try {
                return bytes.length;
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos, MenuImportWorker.Upload.class, "getSize()");
            }
        }

        /**
         * Returns bytes.
         *
         * @return the get bytes result
         */
        public byte[] getBytes() {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuImportWorker.Upload.class, "getBytes()");
            try {
                return bytes;
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos, MenuImportWorker.Upload.class, "getBytes()");
            }
        }

        /**
         * Returns input stream.
         *
         * @return the get input stream result
         */
        public java.io.InputStream getInputStream() {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuImportWorker.Upload.class, "getInputStream()");
            try {
                return new java.io.ByteArrayInputStream(bytes);
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        MenuImportWorker.Upload.class,
                        "getInputStream()");
            }
        }

        /**
         * Transfers to.
         *
         * @param dest the dest
         * @throws java.io.IOException if the operation cannot complete
         */
        public void transferTo(java.io.File dest) throws java.io.IOException {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuImportWorker.Upload.class, "transferTo(java.io.File)");
            try {
                java.nio.file.Files.write(dest.toPath(), bytes);
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        MenuImportWorker.Upload.class,
                        "transferTo(java.io.File)");
            }
        }
    }
}
