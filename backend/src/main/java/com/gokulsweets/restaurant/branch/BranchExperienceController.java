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
     * Handles {@code GET /api/admin/branches/{branchId}/experience} for branch experience.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code experience.get(branchId)}
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
     * Handles {@code PUT /api/admin/branches/{branchId}/experience} for branch experience.
     *
     * @param branchId the branch id supplied to this method
     * @param version the version supplied to this method
     * @param copy the copy supplied to this method
     * @return the value of {@code experience.saveCopy(branchId, copy, version)}
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
     * Handles {@code POST /api/admin/branches/{branchId}/experience/media} for branch experience.
     *
     * @param branchId the branch id supplied to this method
     * @param version the version supplied to this method
     * @param mobile the mobile supplied to this method
     * @param file the file supplied to this method
     * @return the value of {@code experience.upload(branchId, file, mobile, version)}
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
     * Handles {@code POST /api/admin/branches/{branchId}/experience/publish} for branch experience.
     *
     * @param branchId the branch id supplied to this method
     * @param version the version supplied to this method
     * @return the value of {@code experience.publish(branchId, version)}
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
     * Handles {@code GET /api/admin/branches/{branchId}/experience/publications} for branch
     * experience.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code experience.history(branchId)}
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
     * Handles {@code POST
     * /api/admin/branches/{branchId}/experience/publications/{revision}/restore} for branch
     * experience.
     *
     * @param branchId the branch id supplied to this method
     * @param revision the revision supplied to this method
     * @param version the version supplied to this method
     * @return the value of {@code experience.restore(branchId, revision, version)}
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
