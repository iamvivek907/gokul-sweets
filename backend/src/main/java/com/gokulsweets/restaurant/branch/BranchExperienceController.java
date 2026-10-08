package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** HTTP endpoints for branch experience operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branchId}/experience")
public class BranchExperienceController {

    private final BranchExperienceService experience;

    /**
     * Returns the operation.
     *
     * @param branchId the branch id
     * @return the get result
     */
    @GetMapping
    public BranchExperienceService.Snapshot get(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceController.class, "get(long)");
        try {
            return experience.get(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchExperienceController.class, "get(long)");
        }
    }

    /**
     * Drafts the operation.
     *
     * @param branchId the branch id
     * @param version the version
     * @param copy the copy
     * @return the draft result
     */
    @PutMapping
    public BranchExperienceService.Snapshot draft(
            @PathVariable long branchId,
            @RequestHeader("If-Match") long version,
            @Valid @RequestBody BranchExperienceService.Copy copy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BranchExperienceController.class,
                        "draft(long,long,BranchExperienceService.Copy)");
        try {
            return experience.saveCopy(branchId, copy, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchExperienceController.class,
                    "draft(long,long,BranchExperienceService.Copy)");
        }
    }

    /**
     * Uploads the operation.
     *
     * @param branchId the branch id
     * @param version the version
     * @param mobile the mobile
     * @param file the file
     * @return the upload result
     */
    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BranchExperienceService.Snapshot upload(
            @PathVariable long branchId,
            @RequestHeader("If-Match") long version,
            @RequestParam(defaultValue = "false") boolean mobile,
            @RequestParam MultipartFile file) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BranchExperienceController.class,
                        "upload(long,long,boolean,MultipartFile)");
        try {
            return experience.upload(branchId, file, mobile, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchExperienceController.class,
                    "upload(long,long,boolean,MultipartFile)");
        }
    }

    /**
     * Publishes the operation.
     *
     * @param branchId the branch id
     * @param version the version
     * @return the publish result
     */
    @PostMapping("/publish")
    public BranchExperienceService.Snapshot publish(
            @PathVariable long branchId, @RequestHeader("If-Match") long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceController.class, "publish(long,long)");
        try {
            return experience.publish(branchId, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchExperienceController.class,
                    "publish(long,long)");
        }
    }

    /**
     * History the operation.
     *
     * @param branchId the branch id
     * @return the history result
     */
    @GetMapping("/publications")
    public List<BranchExperienceService.Publication> history(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceController.class, "history(long)");
        try {
            return experience.history(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchExperienceController.class, "history(long)");
        }
    }

    /**
     * Restores the operation.
     *
     * @param branchId the branch id
     * @param revision the revision
     * @param version the version
     * @return the restore result
     */
    @PostMapping("/publications/{revision}/restore")
    public BranchExperienceService.Snapshot restore(
            @PathVariable long branchId,
            @PathVariable long revision,
            @RequestHeader("If-Match") long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceController.class, "restore(long,long,long)");
        try {
            return experience.restore(branchId, revision, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchExperienceController.class,
                    "restore(long,long,long)");
        }
    }
}
