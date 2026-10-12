package com.gokulsweets.restaurant.menu.workspace;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.storage.R2StorageService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

/** HTTP endpoints for menu appearance operations. */
@RestController
@RequiredArgsConstructor
public class MenuAppearanceController {

    private final JdbcTemplate jdbc;

    private final StaffAuthorizationService staff;

    private final ObjectMapper mapper;

    private final R2StorageService storage;

    /**
     * Immutable frame data contract.
     *
     * @param x the x
     * @param y the y
     * @param zoom the zoom
     * @param fit the fit
     */
    public record Frame(
            @Min(0) @Max(100) int x,
            @Min(0) @Max(100) int y,
            @Min(100) @Max(300) int zoom,
            @NotNull @Pattern(regexp = "COVER|CONTAIN") String fit) {}

    /**
     * Immutable banner data contract.
     *
     * @param key the key
     * @param titleEn the title en
     * @param titleHi the title hi
     * @param subtitleEn the subtitle en
     * @param subtitleHi the subtitle hi
     * @param buttonLabel the button label
     * @param categoryId the category id
     * @param visible the visible
     * @param order the order
     * @param startAt the start at
     * @param endAt the end at
     * @param mediaUrl the media url
     * @param mediaType the media type
     * @param posterUrl the poster url
     * @param frame the frame
     */
    public record Banner(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,40}") String key,
            @NotBlank @Size(max = 120) String titleEn,
            @Size(max = 120) String titleHi,
            @Size(max = 240) String subtitleEn,
            @Size(max = 240) String subtitleHi,
            @Size(max = 60) String buttonLabel,
            @Positive Long categoryId,
            boolean visible,
            @Min(0) int order,
            Instant startAt,
            Instant endAt,
            @Size(max = 1000) String mediaUrl,
            @Size(max = 30) @Pattern(regexp = "image/(jpeg|png|webp)|video/(mp4|webm)")
                    String mediaType,
            @Size(max = 1000) String posterUrl,
            @NotNull @Valid Frame frame) {}

    /**
     * Immutable category data contract.
     *
     * @param id the id
     * @param order the order
     * @param imageUrl the image url
     */
    public record Category(
            @Positive long id, @Min(0) int order, @Size(max = 1000) String imageUrl) {}

    /**
     * Immutable config data contract.
     *
     * @param banners the banners
     * @param categories the categories
     */
    public record Config(
            @NotNull @Size(max = 12) List<@Valid Banner> banners,
            @NotNull @Size(max = 200) List<@Valid Category> categories) {}

    /**
     * Immutable input data contract.
     *
     * @param version the version
     * @param config the config
     * @param publish the publish
     */
    public record Input(@Min(0) long version, @NotNull @Valid Config config, boolean publish) {}

    /**
     * Immutable snapshot data contract.
     *
     * @param version the version
     * @param draft the draft
     * @param live the live
     * @param publishedAt the published at
     */
    public record Snapshot(long version, Config draft, Config live, String publishedAt) {}

    /**
     * Checks authorization for menu appearance data.
     *
     * <p>Authorization checks include {@code PermissionName.MENU_MANAGE}.
     *
     * @param branch the branch supplied to this method
     */
    private void authorize(long branch) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuAppearanceController.class, "authorize(long)");
        try {
            staff.requirePermission(PermissionName.MENU_MANAGE);
            staff.requireBranchAccess(branch);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuAppearanceController.class, "authorize(long)");
        }
    }

    /**
     * Returns a menu-appearance configuration with no category or product overrides.
     *
     * @return the {@code Config} result
     */
    private Config empty() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuAppearanceController.class, "empty()");
        try {
            return new Config(List.of(), List.of());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuAppearanceController.class, "empty()");
        }
    }

    /**
     * Decodes menu appearance data and returns the {@code Config} result.
     *
     * @param value the value supplied to this method
     * @return the value of {@code mapper.readValue(String.valueOf(value), Config.class)}
     * @throws IllegalStateException when the method rejects the request with {@code Unable to read
     *     menu appearance.}
     */
    private Config decode(Object value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuAppearanceController.class, "decode(Object)");
        try {
            try {
                return mapper.readValue(String.valueOf(value), Config.class);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to read menu appearance.", e);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuAppearanceController.class, "decode(Object)");
        }
    }

    /**
     * Encodes menu appearance data and returns the {@code String} result.
     *
     * @param config the config supplied to this method
     * @return the value of {@code mapper.writeValueAsString(config)}
     * @throws IllegalArgumentException when the method rejects the request with {@code Invalid menu
     *     appearance.}
     */
    private String encode(Config config) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuAppearanceController.class, "encode(Config)");
        try {
            try {
                return mapper.writeValueAsString(config);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid menu appearance.", e);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuAppearanceController.class, "encode(Config)");
        }
    }

    /**
     * Returns read information for menu appearance.
     *
     * <p>Reads {@code branch_menu_appearance}.
     *
     * @param branch the branch supplied to this method
     * @return the {@code Snapshot} result
     */
    private Snapshot read(long branch) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuAppearanceController.class, "read(long)");
        try {
            var rows =
                    jdbc.queryForList(
                            "SELECT * FROM branch_menu_appearance WHERE branch_id=?", branch);
            if (rows.isEmpty()) return new Snapshot(0, empty(), empty(), null);
            var r = rows.getFirst();
            return new Snapshot(
                    ((Number) r.get("version")).longValue(),
                    decode(r.get("draft")),
                    decode(r.get("live")),
                    r.get("published_at") == null ? null : r.get("published_at").toString());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuAppearanceController.class, "read(long)");
        }
    }

    /**
     * Handles {@code GET /api/admin/branches/{branch}/menu/workspace/appearance} for menu
     * appearance.
     *
     * @param branch the branch supplied to this method
     * @return the value of {@code read(branch)}
     */
    @GetMapping("/api/admin/branches/{branch}/menu/workspace/appearance")
    @Transactional(readOnly = true)
    public Snapshot admin(@PathVariable long branch) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuAppearanceController.class, "admin(long)");
        try {
            authorize(branch);
            return read(branch);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuAppearanceController.class, "admin(long)");
        }
    }

    /**
     * Handles {@code GET /api/menu/appearance} for menu appearance.
     *
     * <p>Reads {@code branches}.
     *
     * @param branchId the branch id supplied to this method
     * @return the {@code Config} result
     * @throws ResponseStatusException when the method rejects the request with {@code Branch
     *     unavailable.}
     */
    @GetMapping("/api/menu/appearance")
    @Transactional(readOnly = true)
    public Config live(@RequestParam long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuAppearanceController.class, "live(long)");
        try {
            if (!Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM branches WHERE id=? AND active=true)",
                            Boolean.class,
                            branchId)))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch unavailable.");
            var c = read(branchId).live();
            Instant now = Instant.now();
            return new Config(
                    c.banners().stream()
                            .filter(
                                    b ->
                                            b.visible()
                                                    && (b.startAt() == null
                                                            || !now.isBefore(b.startAt()))
                                                    && (b.endAt() == null
                                                            || now.isBefore(b.endAt())))
                            .sorted(Comparator.comparingInt(Banner::order))
                            .toList(),
                    c.categories());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuAppearanceController.class, "live(long)");
        }
    }

    /**
     * Returns managed information for menu appearance.
     *
     * <p>Delegates to {@code storage.validateManagedCampaignUrl(...)}.
     *
     * @param url the url supplied to this method
     */
    private void managed(String url) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuAppearanceController.class, "managed(String)");
        try {
            if (url == null || url.isBlank()) return;
            storage.validateManagedCampaignUrl(url);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuAppearanceController.class, "managed(String)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/branches/{branch}/menu/workspace/appearance} for menu
     * appearance.
     *
     * <p>Reads {@code branch_menu_appearance}.
     *
     * <p>Writes {@code branch_menu_appearance}, {@code menu_workspace_audit}.
     *
     * @param branch the branch supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code read(branch)}
     * @throws IllegalArgumentException when the method rejects the request with {@code Choose a
     *     destination category for the button.}; {@code Duplicate banner key.}; {@code Duplicate
     *     category.}; {@code End must follow start.}; {@code Video banners need a static poster for
     *     reduced motion.}
     * @throws ResponseStatusException when the method rejects the request with {@code Appearance
     *     changed. Reload before saving; draft retained.}
     */
    @PutMapping("/api/admin/branches/{branch}/menu/workspace/appearance")
    @Transactional
    public Snapshot save(@PathVariable long branch, @Valid @RequestBody Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuAppearanceController.class, "save(long,Input)");
        try {
            authorize(branch);
            Set<String> keys = new HashSet<>();
            Set<Long> categoryIds = new HashSet<>();
            for (var b : input.config().banners()) {
                if (!keys.add(b.key())) throw new IllegalArgumentException("Duplicate banner key.");
                managed(b.mediaUrl());
                managed(b.posterUrl());
                if (b.startAt() != null && b.endAt() != null && !b.endAt().isAfter(b.startAt()))
                    throw new IllegalArgumentException("End must follow start.");
                if (b.categoryId() != null) category(branch, b.categoryId());
                if (b.buttonLabel() != null && !b.buttonLabel().isBlank() && b.categoryId() == null)
                    throw new IllegalArgumentException(
                            "Choose a destination category for the button.");
                if (input.publish()
                        && b.visible()
                        && b.mediaType() != null
                        && b.mediaType().startsWith("video/")
                        && (b.posterUrl() == null || b.posterUrl().isBlank()))
                    throw new IllegalArgumentException(
                            "Video banners need a static poster for reduced motion.");
            }
            for (var c : input.config().categories()) {
                if (!categoryIds.add(c.id()))
                    throw new IllegalArgumentException("Duplicate category.");
                category(branch, c.id());
                managed(c.imageUrl());
            }
            jdbc.update(
                    "INSERT INTO branch_menu_appearance(branch_id) VALUES (?) ON CONFLICT DO"
                            + " NOTHING",
                    branch);
            var old =
                    jdbc.queryForMap(
                            "SELECT * FROM branch_menu_appearance WHERE branch_id=? FOR UPDATE",
                            branch);
            if (((Number) old.get("version")).longValue() != input.version())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Appearance changed. Reload before saving; draft retained.");
            String config = encode(input.config());
            jdbc.update(
                    "UPDATE branch_menu_appearance SET draft=?::jsonb,live=CASE WHEN ? THEN"
                            + " ?::jsonb ELSE live END,published_at=CASE WHEN ? THEN now() ELSE"
                            + " published_at END,version=version+1 WHERE branch_id=?",
                    config,
                    input.publish(),
                    config,
                    input.publish(),
                    branch);
            jdbc.update(
                    "INSERT INTO"
                        + " menu_workspace_audit(actor,branch_id,action,before_state,after_state)"
                        + " VALUES (?, ?, ?, ?, ?)",
                    staff.getCurrentStaff().getUsername(),
                    branch,
                    input.publish() ? "APPEARANCE_PUBLISH" : "APPEARANCE_DRAFT",
                    String.valueOf(old.get("draft")),
                    config);
            return read(branch);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuAppearanceController.class, "save(long,Input)");
        }
    }

    /**
     * Returns category information for menu appearance.
     *
     * <p>Reads {@code branch_products}, {@code products}.
     *
     * @param branch the branch supplied to this method
     * @param id the id supplied to this method
     * @throws IllegalArgumentException when the method rejects the request with {@code Choose a
     *     category in this branch menu.}
     */
    private void category(long branch, long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuAppearanceController.class, "category(long,long)");
        try {
            if (!Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM branch_products bp JOIN products p ON"
                                + " p.id=bp.product_id WHERE bp.branch_id=? AND p.category_id=?)",
                            Boolean.class,
                            branch,
                            id)))
                throw new IllegalArgumentException("Choose a category in this branch menu.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuAppearanceController.class,
                    "category(long,long)");
        }
    }

    /**
     * Handles {@code POST /api/admin/branches/{branch}/menu/workspace/appearance/media} for menu
     * appearance.
     *
     * <p>Delegates to {@code storage.uploadCampaignMedia(...)}.
     *
     * @param branch the branch supplied to this method
     * @param file the file supplied to this method
     * @param poster the poster supplied to this method
     * @return the value of {@code storage.uploadCampaignMedia(branch, file, poster)}
     * @throws IllegalArgumentException when the method rejects the request with {@code Choose a
     *     JPEG, PNG, WebP, MP4 or WebM file.}
     */
    @PostMapping(
            value = "/api/admin/branches/{branch}/menu/workspace/appearance/media",
            consumes = "multipart/form-data")
    public R2StorageService.CampaignMedia media(
            @PathVariable long branch,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "false") boolean poster) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuAppearanceController.class, "media(long,MultipartFile,boolean)");
        try {
            authorize(branch);
            if (file.getContentType() == null
                    || !Set.of("image/jpeg", "image/png", "image/webp", "video/mp4", "video/webm")
                            .contains(file.getContentType()))
                throw new IllegalArgumentException("Choose a JPEG, PNG, WebP, MP4 or WebM file.");
            return storage.uploadCampaignMedia(branch, file, poster);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuAppearanceController.class,
                    "media(long,MultipartFile,boolean)");
        }
    }
}
