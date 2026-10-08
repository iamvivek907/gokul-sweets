package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** HTTP endpoints for menu import operations. */
@RestController
@RequestMapping("/api/admin/branches/{branchId}/menu")
@RequiredArgsConstructor
public class MenuImportController {

    private final MenuImportJobs jobs;

    /**
     * Jobs the operation.
     *
     * @param branchId the branch id
     * @param jobId the job id
     * @return the job result
     */
    @GetMapping("/import/jobs/{jobId}")
    public MenuImportJobs.Job job(@PathVariable long branchId, @PathVariable java.util.UUID jobId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportController.class, "job(long,java.util.UUID)");
        try {
            return jobs.get(branchId, jobId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuImportController.class,
                    "job(long,java.util.UUID)");
        }
    }

    /**
     * Submissions the operation.
     *
     * @param branchId the branch id
     * @param submissionId the submission id
     * @return the submission result
     */
    @GetMapping("/import/submissions/{submissionId}")
    public MenuImportJobs.Job submission(
            @PathVariable long branchId, @PathVariable java.util.UUID submissionId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportController.class, "submission(long,java.util.UUID)");
        try {
            return jobs.getSubmission(branchId, submissionId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuImportController.class,
                    "submission(long,java.util.UUID)");
        }
    }

    private final MenuImportService menuImportService;

    private final MenuTemplateService menuTemplateService;

    /**
     * Downloads template.
     *
     * @param branchId the branch id
     * @return the download template result
     */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuImportController.class, "downloadTemplate(Long)");
        try {
            byte[] content = menuTemplateService.createTemplate(branchId);
            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"gokul-menu-template.xlsx\"")
                    .contentType(
                            MediaType.parseMediaType(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(content);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuImportController.class,
                    "downloadTemplate(Long)");
        }
    }

    /**
     * Validates import.
     *
     * @param branchId the branch id
     * @param file the file
     * @param submissionId the submission id
     * @return the validate import result
     */
    @PostMapping(value = "/import/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> validateImport(
            @PathVariable Long branchId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) java.util.UUID submissionId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuImportController.class,
                        "validateImport(Long,MultipartFile,java.util.UUID)");
        try {
            if (jobs.enabled())
                return ResponseEntity.accepted()
                        .body(jobs.enqueue(branchId, file, "VALIDATE", submissionId));
            return ResponseEntity.ok(menuImportService.validate(branchId, file));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuImportController.class,
                    "validateImport(Long,MultipartFile,java.util.UUID)");
        }
    }

    /**
     * Imports menu.
     *
     * @param branchId the branch id
     * @param file the file
     * @param submissionId the submission id
     * @return the import menu result
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importMenu(
            @PathVariable Long branchId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) java.util.UUID submissionId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuImportController.class,
                        "importMenu(Long,MultipartFile,java.util.UUID)");
        try {
            if (jobs.enabled())
                return ResponseEntity.accepted()
                        .body(jobs.enqueue(branchId, file, "IMPORT", submissionId));
            return ResponseEntity.ok(menuImportService.importMenu(branchId, file));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuImportController.class,
                    "importMenu(Long,MultipartFile,java.util.UUID)");
        }
    }
}
