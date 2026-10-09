package com.gokulsweets.restaurant.menu.workspace;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** HTTP endpoints for menu workspace operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branch}/menu/workspace")
public class MenuWorkspaceController {

    private final MenuWorkspaceService service;

    /**
     * Handles {@code GET /api/admin/branches/{branch}/menu/workspace} for menu workspace.
     *
     * <p>Delegates to {@code service.list(...)}.
     *
     * @param branch the branch supplied to this method
     * @param date the date supplied to this method
     * @param search the search supplied to this method
     * @param category the category supplied to this method
     * @param filter the filter supplied to this method
     * @param page the page supplied to this method
     * @param size the size supplied to this method
     * @return the value of {@code service.list(branch, date, search, category, filter, page, size)}
     */
    @GetMapping
    public MenuWorkspaceService.Page list(
            @PathVariable long branch,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Long category,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuWorkspaceController.class,
                        "list(long,LocalDate,String,Long,String,int,int)");
        try {
            return service.list(branch, date, search, category, filter, page, size);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceController.class,
                    "list(long,LocalDate,String,Long,String,int,int)");
        }
    }

    /**
     * Immutable create data contract.
     *
     * @param details the details
     * @param branchIds the branch ids
     */
    public record Create(
            @NotNull @Valid MenuWorkspaceService.Details details,
            @NotNull @Size(min = 1, max = 50) List<@Positive Long> branchIds) {}

    /**
     * Handles {@code POST /api/admin/branches/{branch}/menu/workspace} for menu workspace.
     *
     * <p>Delegates to {@code service.create(...)}.
     *
     * @param branch the branch supplied to this method
     * @param input the input supplied to this method
     * @return the {@code Map<String, Long>} result
     */
    @PostMapping
    public Map<String, Long> create(@PathVariable long branch, @Valid @RequestBody Create input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkspaceController.class, "create(long,Create)");
        try {
            return Map.of("productId", service.create(branch, input.details(), input.branchIds()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceController.class,
                    "create(long,Create)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/branches/{branch}/menu/workspace/{id}/details} for menu
     * workspace.
     *
     * <p>Delegates to {@code service.editDetails(...)}.
     *
     * @param branch the branch supplied to this method
     * @param id the id supplied to this method
     * @param input the input supplied to this method
     */
    @PutMapping("/{id}/details")
    public void details(
            @PathVariable long branch,
            @PathVariable long id,
            @Valid @RequestBody MenuWorkspaceService.Details input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuWorkspaceController.class,
                        "details(long,long,MenuWorkspaceService.Details)");
        try {
            service.editDetails(branch, id, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceController.class,
                    "details(long,long,MenuWorkspaceService.Details)");
        }
    }

    /**
     * Edits branch.
     *
     * @param branch the branch
     * @param id the id
     * @param input the input
     */
    @PatchMapping("/{id}/branch")
    public void editBranch(
            @PathVariable long branch,
            @PathVariable long id,
            @Valid @RequestBody MenuWorkspaceService.BranchEdit input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuWorkspaceController.class,
                        "editBranch(long,long,MenuWorkspaceService.BranchEdit)");
        try {
            service.editBranch(branch, id, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceController.class,
                    "editBranch(long,long,MenuWorkspaceService.BranchEdit)");
        }
    }

    /**
     * Handles {@code PATCH /api/admin/branches/{branch}/menu/workspace/{id}/dietary} for menu
     * workspace.
     *
     * <p>Delegates to {@code service.editDietary(...)}.
     *
     * @param branch the branch supplied to this method
     * @param id the id supplied to this method
     * @param input the input supplied to this method
     */
    @PatchMapping("/{id}/dietary")
    public void dietary(
            @PathVariable long branch,
            @PathVariable long id,
            @Valid @RequestBody MenuWorkspaceService.DietaryEdit input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuWorkspaceController.class,
                        "dietary(long,long,MenuWorkspaceService.DietaryEdit)");
        try {
            service.editDietary(branch, id, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceController.class,
                    "dietary(long,long,MenuWorkspaceService.DietaryEdit)");
        }
    }

    /**
     * Deletes item.
     *
     * @param branch the branch
     * @param id the id
     * @param version the version
     */
    @DeleteMapping("/{id}")
    public void deleteItem(
            @PathVariable long branch, @PathVariable long id, @RequestParam @Min(0) long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkspaceController.class, "deleteItem(long,long,long)");
        try {
            service.deleteBranchItem(branch, id, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceController.class,
                    "deleteItem(long,long,long)");
        }
    }

    /**
     * Handles {@code POST /api/admin/branches/{branch}/menu/workspace/{id}/image} for menu
     * workspace.
     *
     * <p>Delegates to {@code service.image(...)}.
     *
     * @param branch the branch supplied to this method
     * @param id the id supplied to this method
     * @param version the version supplied to this method
     * @param image the image supplied to this method
     * @return the {@code Map<String, String>} result
     */
    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    public Map<String, String> image(
            @PathVariable long branch,
            @PathVariable long id,
            @RequestParam long version,
            @RequestParam MultipartFile image) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuWorkspaceController.class, "image(long,long,long,MultipartFile)");
        try {
            return Map.of("imageUrl", service.image(branch, id, version, image, false));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceController.class,
                    "image(long,long,long,MultipartFile)");
        }
    }

    /**
     * Handles {@code DELETE /api/admin/branches/{branch}/menu/workspace/{id}/image} for menu
     * workspace.
     *
     * <p>Delegates to {@code service.image(...)}.
     *
     * @param branch the branch supplied to this method
     * @param id the id supplied to this method
     * @param version the version supplied to this method
     */
    @DeleteMapping("/{id}/image")
    public void remove(
            @PathVariable long branch, @PathVariable long id, @RequestParam long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkspaceController.class, "remove(long,long,long)");
        try {
            service.image(branch, id, version, null, true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceController.class,
                    "remove(long,long,long)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/branches/{branch}/menu/workspace/{id}/stock/{date}} for menu
     * workspace.
     *
     * <p>Delegates to {@code service.stock(...)}.
     *
     * @param branch the branch supplied to this method
     * @param id the id supplied to this method
     * @param date the date supplied to this method
     * @param input the input supplied to this method
     */
    @PutMapping("/{id}/stock/{date}")
    public void stock(
            @PathVariable long branch,
            @PathVariable long id,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody MenuWorkspaceService.StockEdit input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuWorkspaceController.class,
                        "stock(long,long,LocalDate,MenuWorkspaceService.StockEdit)");
        try {
            service.stock(branch, id, date, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceController.class,
                    "stock(long,long,LocalDate,MenuWorkspaceService.StockEdit)");
        }
    }
}
