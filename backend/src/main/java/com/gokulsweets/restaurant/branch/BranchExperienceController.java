package com.gokulsweets.restaurant.branch;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branchId}/experience")
public class BranchExperienceController {
    private final BranchExperienceService experience;

    @GetMapping
    public BranchExperienceService.Snapshot get(@PathVariable long branchId) {
        return experience.get(branchId);
    }

    @PutMapping
    public BranchExperienceService.Snapshot draft(@PathVariable long branchId,
            @RequestHeader("If-Match") long version,
            @Valid @RequestBody BranchExperienceService.Copy copy) {
        return experience.saveCopy(branchId, copy, version);
    }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BranchExperienceService.Snapshot upload(@PathVariable long branchId,
            @RequestHeader("If-Match") long version,
            @RequestParam(defaultValue = "false") boolean mobile, @RequestParam MultipartFile file) {
        return experience.upload(branchId, file, mobile, version);
    }

    @PostMapping("/publish")
    public BranchExperienceService.Snapshot publish(@PathVariable long branchId,
            @RequestHeader("If-Match") long version) {
        return experience.publish(branchId, version);
    }

    @GetMapping("/publications")
    public List<BranchExperienceService.Publication> history(@PathVariable long branchId) {
        return experience.history(branchId);
    }

    @PostMapping("/publications/{revision}/restore")
    public BranchExperienceService.Snapshot restore(@PathVariable long branchId,
            @PathVariable long revision, @RequestHeader("If-Match") long version) {
        return experience.restore(branchId, revision, version);
    }
}
