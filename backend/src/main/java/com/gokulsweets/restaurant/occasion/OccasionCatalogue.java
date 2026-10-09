package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Backend occasion catalogue contract and implementation. */
@Service
@RequiredArgsConstructor
public class OccasionCatalogue {

    private final JdbcTemplate jdbc;

    private final EnhancementProperties features;

    private final Clock clock;

    private final tools.jackson.databind.ObjectMapper mapper =
            new tools.jackson.databind.ObjectMapper();

    /**
     * Immutable sweet data contract.
     *
     * @param id the id
     * @param name the name
     * @param description the description
     * @param imageUrl the image url
     * @param saleMode the sale mode
     * @param occasionOnly the occasion only
     * @param published the published
     * @param leadDays the lead days
     * @param pieceGrams the piece grams
     * @param categoryId the category id
     * @param categoryName the category name
     * @param unitPrice the unit price
     * @param taxPercent the tax percent
     */
    public record Sweet(
            long id,
            String name,
            String description,
            String imageUrl,
            String saleMode,
            boolean occasionOnly,
            boolean published,
            int leadDays,
            BigDecimal pieceGrams,
            Long categoryId,
            String categoryName,
            BigDecimal unitPrice,
            BigDecimal taxPercent) {

        /**
         * Creates a sweet instance.
         *
         * @param id the id
         * @param name the name
         * @param description the description
         * @param imageUrl the image url
         * @param saleMode the sale mode
         * @param occasionOnly the occasion only
         * @param published the published
         * @param leadDays the lead days
         * @param pieceGrams the piece grams
         */
        public Sweet(
                long id,
                String name,
                String description,
                String imageUrl,
                String saleMode,
                boolean occasionOnly,
                boolean published,
                int leadDays,
                BigDecimal pieceGrams) {
            this(
                    id,
                    name,
                    description,
                    imageUrl,
                    saleMode,
                    occasionOnly,
                    published,
                    leadDays,
                    pieceGrams,
                    null,
                    "Selection",
                    null,
                    null);
        }
    }

    /**
     * Immutable box data contract.
     *
     * @param id the id
     * @param name the name
     * @param imageUrl the image url
     * @param dimensions the dimensions
     * @param material the material
     * @param compartments the compartments
     * @param capacityPieces the capacity pieces
     * @param price the price
     * @param branding the branding
     * @param leadDays the lead days
     * @param published the published
     * @param imageUrls the image urls
     * @param capacityGrams the capacity grams
     */
    public record Box(
            Long id,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1000) String imageUrl,
            @NotBlank @Size(max = 100) String dimensions,
            @NotBlank @Size(max = 100) String material,
            @Min(1) @Max(100) int compartments,
            @Min(1) @Max(1000) int capacityPieces,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @Size(max = 300) String branding,
            @Min(0) @Max(365) int leadDays,
            boolean published,
            @Size(max = 6) List<@NotBlank @Size(max = 1000) String> imageUrls,
            @Min(250) @Max(1000) Integer capacityGrams) {

        /**
         * Creates a box instance.
         *
         * @param id the id
         * @param name the name
         * @param imageUrl the image url
         * @param dimensions the dimensions
         * @param material the material
         * @param compartments the compartments
         * @param capacityPieces the capacity pieces
         * @param price the price
         * @param branding the branding
         * @param leadDays the lead days
         * @param published the published
         */
        public Box(
                Long id,
                String name,
                String imageUrl,
                String dimensions,
                String material,
                int compartments,
                int capacityPieces,
                BigDecimal price,
                String branding,
                int leadDays,
                boolean published) {
            this(
                    id,
                    name,
                    imageUrl,
                    dimensions,
                    material,
                    compartments,
                    capacityPieces,
                    price,
                    branding,
                    leadDays,
                    published,
                    List.of(),
                    null);
        }

        /**
         * Creates a box instance.
         *
         * @param id the id
         * @param name the name
         * @param imageUrl the image url
         * @param dimensions the dimensions
         * @param material the material
         * @param compartments the compartments
         * @param capacityPieces the capacity pieces
         * @param price the price
         * @param branding the branding
         * @param leadDays the lead days
         * @param published the published
         * @param imageUrls the image urls
         */
        public Box(
                Long id,
                String name,
                String imageUrl,
                String dimensions,
                String material,
                int compartments,
                int capacityPieces,
                BigDecimal price,
                String branding,
                int leadDays,
                boolean published,
                List<String> imageUrls) {
            this(
                    id,
                    name,
                    imageUrl,
                    dimensions,
                    material,
                    compartments,
                    capacityPieces,
                    price,
                    branding,
                    leadDays,
                    published,
                    imageUrls,
                    null);
        }
    }

    /**
     * Immutable packing group data contract.
     *
     * @param kind the kind
     * @param boxId the box id
     * @param boxCount the box count
     * @param recipe the recipe
     * @param productId the product id
     * @param totalGrams the total grams
     * @param packGrams the pack grams
     * @param includeSpoons the include spoons
     */
    public record PackingGroup(
            String kind,
            Long boxId,
            int boxCount,
            List<Recipe> recipe,
            Long productId,
            BigDecimal totalGrams,
            Integer packGrams,
            boolean includeSpoons) {}

    /**
     * Immutable packed group data contract.
     *
     * @param groupNumber the group number
     * @param kind the kind
     * @param box the box
     * @param boxCount the box count
     * @param recipe the recipe
     * @param productId the product id
     * @param productName the product name
     * @param totalGrams the total grams
     * @param packGrams the pack grams
     * @param includeSpoons the include spoons
     * @param packagingEstimate the packaging estimate
     */
    public record PackedGroup(
            int groupNumber,
            String kind,
            Box box,
            int boxCount,
            List<Recipe> recipe,
            Long productId,
            String productName,
            BigDecimal totalGrams,
            Integer packGrams,
            boolean includeSpoons,
            BigDecimal packagingEstimate) {}

    /**
     * Immutable branding data contract.
     *
     * @param headline the headline
     * @param description the description
     * @param imageUrl the image url
     * @param published the published
     */
    public record Branding(
            @NotNull @Size(max = 100) String headline,
            @NotNull @Size(max = 500) String description,
            @Size(max = 1000) String imageUrl,
            boolean published) {}

    /**
     * Immutable catalogue data contract.
     *
     * @param sweets the sweets
     * @param boxes the boxes
     * @param branding the branding
     */
    public record Catalogue(List<Sweet> sweets, List<Box> boxes, Branding branding) {

        /**
         * Creates a catalogue instance.
         *
         * @param sweets the sweets
         * @param boxes the boxes
         */
        public Catalogue(List<Sweet> sweets, List<Box> boxes) {
            this(sweets, boxes, null);
        }
    }

    /**
     * Returns images information for occasion catalogue.
     *
     * @param json the json supplied to this method
     * @return the {@code List<String>} result
     * @throws IllegalStateException when the method rejects the request with {@code Invalid
     *     packaging gallery}
     */
    private List<String> images(String json) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionCatalogue.class, "images(String)");
        try {
            try {
                return mapper.readValue(
                        json, new tools.jackson.core.type.TypeReference<List<String>>() {});
            } catch (Exception error) {
                throw new IllegalStateException("Invalid packaging gallery", error);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionCatalogue.class, "images(String)");
        }
    }

    /**
     * Serializes gallery URLs as JSON, substituting an empty list for a null list and rejecting
     * serialization failures.
     *
     * @param urls the urls supplied to this method
     * @return the value of {@code mapper.writeValueAsString(urls == null ? List.of() : urls)}
     * @throws IllegalArgumentException when the method rejects the request with {@code Invalid
     *     gallery}
     */
    private String json(List<String> urls) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionCatalogue.class, "json(List<String>)");
        try {
            try {
                return mapper.writeValueAsString(urls == null ? List.of() : urls);
            } catch (Exception error) {
                throw new IllegalArgumentException("Invalid gallery", error);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionCatalogue.class, "json(List<String>)");
        }
    }

    /**
     * Returns photo information for occasion catalogue.
     *
     * @param url the url supplied to this method
     * @throws ResponseStatusException when the method rejects the request with {@code Use an HTTPS
     *     photo.}
     */
    private void photo(String url) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionCatalogue.class, "photo(String)");
        try {
            if (url != null && !url.isBlank() && !url.startsWith("https://"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use an HTTPS photo.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionCatalogue.class, "photo(String)");
        }
    }

    /**
     * Immutable sweet settings data contract.
     *
     * @param occasionOnly the occasion only
     * @param published the published
     * @param leadDays the lead days
     * @param pieceGrams the piece grams
     */
    public record SweetSettings(
            boolean occasionOnly,
            boolean published,
            @Min(0) @Max(365) int leadDays,
            @DecimalMin("0.001") @Digits(integer = 6, fraction = 3) BigDecimal pieceGrams) {}

    /**
     * Immutable recipe data contract.
     *
     * @param productId the product id
     * @param pieces the pieces
     */
    public record Recipe(long productId, int pieces) {}

    /**
     * Immutable gift request data contract.
     *
     * @param boxId the box id
     * @param boxCount the box count
     * @param recipe the recipe
     * @param includeSpoons the include spoons
     */
    public record GiftRequest(
            long boxId, int boxCount, List<Recipe> recipe, boolean includeSpoons) {

        /**
         * Creates a gift request instance.
         *
         * @param boxId the box id
         * @param boxCount the box count
         * @param recipe the recipe
         */
        public GiftRequest(long boxId, int boxCount, List<Recipe> recipe) {
            this(boxId, boxCount, recipe, false);
        }
    }

    /**
     * Immutable gift snapshot data contract.
     *
     * @param box the box
     * @param boxCount the box count
     * @param recipe the recipe
     * @param packagingEstimate the packaging estimate
     * @param approvedPackagingTotal the approved packaging total
     * @param includeSpoons the include spoons
     */
    public record GiftSnapshot(
            Box box,
            int boxCount,
            List<Recipe> recipe,
            BigDecimal packagingEstimate,
            BigDecimal approvedPackagingTotal,
            boolean includeSpoons) {

        /**
         * Creates a gift snapshot instance.
         *
         * @param box the box
         * @param boxCount the box count
         * @param recipe the recipe
         * @param packagingEstimate the packaging estimate
         * @param approvedPackagingTotal the approved packaging total
         */
        public GiftSnapshot(
                Box box,
                int boxCount,
                List<Recipe> recipe,
                BigDecimal packagingEstimate,
                BigDecimal approvedPackagingTotal) {
            this(box, boxCount, recipe, packagingEstimate, approvedPackagingTotal, false);
        }
    }

    /** Rejects access when the feature is disabled. */
    void enabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionCatalogue.class, "enabled()");
        try {
            if (!features.isOccasionEnquiries())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, OccasionCatalogue.class, "enabled()");
        }
    }

    /**
     * Returns catalogue information for occasion catalogue.
     *
     * <p>Reads {@code branch_products}, {@code branches}, {@code categories}, {@code
     * occasion_branding}, {@code occasion_packaging}, {@code products}, {@code tax_categories}.
     *
     * @param branchId the branch id supplied to this method
     * @param admin the admin supplied to this method
     * @return the {@code Catalogue} result
     * @throws ResponseStatusException when the method rejects the request with {@code This branch
     *     is currently not operational.}
     */
    @Transactional(readOnly = true)
    public Catalogue catalogue(long branchId, boolean admin) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionCatalogue.class, "catalogue(long,boolean)");
        try {
            enabled();
            if (!admin
                    && !Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT EXISTS(SELECT 1 FROM branches WHERE id=? AND active AND"
                                            + " operational)",
                                    Boolean.class,
                                    branchId)))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "This branch is currently not operational.");
            var sweets =
                    jdbc.query(
                            """
SELECT p.id,p.name,p.description,p.image_url,p.sale_mode,bp.occasion_only,bp.occasion_published,
       bp.occasion_lead_days,bp.occasion_piece_grams,p.category_id,c.name,COALESCE(bp.price_override,p.base_price),tc.cgst_rate+tc.sgst_rate
FROM branch_products bp JOIN products p ON p.id=bp.product_id JOIN branches b ON b.id=bp.branch_id JOIN categories c ON c.id=p.category_id JOIN tax_categories tc ON tc.id=p.tax_category_id AND tc.active
WHERE bp.branch_id=? AND b.active AND p.active AND (? OR bp.occasion_published)
ORDER BY bp.occasion_only DESC,bp.display_order,p.name
""",
                            (rs, n) ->
                                    new Sweet(
                                            rs.getLong(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getString(4),
                                            rs.getString(5),
                                            rs.getBoolean(6),
                                            rs.getBoolean(7),
                                            rs.getInt(8),
                                            rs.getBigDecimal(9),
                                            rs.getLong(10),
                                            rs.getString(11),
                                            rs.getBigDecimal(12),
                                            rs.getBigDecimal(13)),
                            branchId,
                            admin);
            var boxes =
                    jdbc.query(
                            "SELECT * FROM occasion_packaging WHERE branch_id=? AND (? OR"
                                    + " published) ORDER BY id",
                            (rs, n) ->
                                    new Box(
                                            rs.getLong("id"),
                                            rs.getString("name"),
                                            rs.getString("image_url"),
                                            rs.getString("dimensions"),
                                            rs.getString("material"),
                                            rs.getInt("compartments"),
                                            rs.getInt("capacity_pieces"),
                                            rs.getBigDecimal("price"),
                                            rs.getString("branding"),
                                            rs.getInt("lead_days"),
                                            rs.getBoolean("published"),
                                            images(rs.getString("image_urls")),
                                            rs.getObject("capacity_grams", Integer.class)),
                            branchId,
                            admin);
            var branding =
                    jdbc.query(
                            "SELECT * FROM occasion_branding WHERE branch_id=? AND (? OR"
                                    + " published)",
                            (rs, n) ->
                                    new Branding(
                                            rs.getString("headline"),
                                            rs.getString("description"),
                                            rs.getString("image_url"),
                                            rs.getBoolean("published")),
                            branchId,
                            admin);
            return new Catalogue(sweets, boxes, branding.isEmpty() ? null : branding.getFirst());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionCatalogue.class, "catalogue(long,boolean)");
        }
    }

    /**
     * Configures sweet.
     *
     * @param branchId the branch id
     * @param productId the product id
     * @param input the input
     */
    @Transactional
    public void configureSweet(long branchId, long productId, SweetSettings input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionCatalogue.class, "configureSweet(long,long,SweetSettings)");
        try {
            enabled();
            if (jdbc.update(
                            "UPDATE branch_products SET"
                                + " occasion_only=?,occasion_published=?,occasion_lead_days=?,occasion_piece_grams=?"
                                + " WHERE branch_id=? AND product_id=?",
                            input.occasionOnly(),
                            input.published(),
                            input.leadDays(),
                            input.pieceGrams(),
                            branchId,
                            productId)
                    != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionCatalogue.class,
                    "configureSweet(long,long,SweetSettings)");
        }
    }

    /**
     * Saves box.
     *
     * @param branchId the branch id
     * @param box the box
     * @return the save box result
     */
    @Transactional
    public Box saveBox(long branchId, Box box) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionCatalogue.class, "saveBox(long,Box)");
        try {
            enabled();
            if (box.capacityGrams() != null
                    && !Set.of(250, 500, 1000).contains(box.capacityGrams()))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Choose 250 g, 500 g or 1 kg food capacity.");
            if (box.imageUrl() != null
                    && !box.imageUrl().isBlank()
                    && !box.imageUrl().startsWith("https://"))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Use an HTTPS URL for a real packaging photo.");
            if (box.imageUrls() != null) box.imageUrls().forEach(this::photo);
            if (box.id() == null) {
                Long id =
                        jdbc.queryForObject(
                                """
INSERT INTO occasion_packaging(branch_id,name,image_url,dimensions,material,compartments,capacity_pieces,price,branding,lead_days,published,image_urls,capacity_grams)
VALUES(?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?) RETURNING id
""",
                                Long.class,
                                branchId,
                                box.name(),
                                box.imageUrl(),
                                box.dimensions(),
                                box.material(),
                                box.compartments(),
                                box.capacityPieces(),
                                box.price(),
                                box.branding(),
                                box.leadDays(),
                                box.published(),
                                json(box.imageUrls()),
                                box.capacityGrams());
                return new Box(
                        id,
                        box.name(),
                        box.imageUrl(),
                        box.dimensions(),
                        box.material(),
                        box.compartments(),
                        box.capacityPieces(),
                        box.price(),
                        box.branding(),
                        box.leadDays(),
                        box.published(),
                        box.imageUrls(),
                        box.capacityGrams());
            }
            if (jdbc.update(
                            """
UPDATE occasion_packaging SET name=?,image_url=?,dimensions=?,material=?,compartments=?,capacity_pieces=?,price=?,branding=?,lead_days=?,published=?,image_urls=?::jsonb,capacity_grams=? WHERE id=? AND branch_id=?
""",
                            box.name(),
                            box.imageUrl(),
                            box.dimensions(),
                            box.material(),
                            box.compartments(),
                            box.capacityPieces(),
                            box.price(),
                            box.branding(),
                            box.leadDays(),
                            box.published(),
                            json(box.imageUrls()),
                            box.capacityGrams(),
                            box.id(),
                            branchId)
                    != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return box;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionCatalogue.class, "saveBox(long,Box)");
        }
    }

    /**
     * Saves branding.
     *
     * @param branchId the branch id
     * @param input the input
     */
    @Transactional
    public void saveBranding(long branchId, Branding input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionCatalogue.class, "saveBranding(long,Branding)");
        try {
            enabled();
            photo(input.imageUrl());
            jdbc.update(
                    "INSERT INTO"
                        + " occasion_branding(branch_id,headline,description,image_url,published)"
                        + " VALUES (?,?,?,?,?) ON CONFLICT(branch_id) DO UPDATE SET"
                        + " headline=EXCLUDED.headline,description=EXCLUDED.description,image_url=EXCLUDED.image_url,published=EXCLUDED.published",
                    branchId,
                    input.headline(),
                    input.description(),
                    input.imageUrl(),
                    input.published());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionCatalogue.class,
                    "saveBranding(long,Branding)");
        }
    }

    /**
     * Validates gift.
     *
     * @param branchId the branch id
     * @param date the date
     * @param items the items
     * @param gift the gift
     * @return the validate gift result
     */
    public GiftSnapshot validateGift(
            long branchId,
            LocalDate date,
            List<OccasionEnquiryService.Item> items,
            GiftRequest gift) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionCatalogue.class,
                        "validateGift(long,LocalDate,List<OccasionEnquiryService.Item>,GiftRequest)");
        try {
            if (gift == null) return null;
            if (gift.boxCount() < 1
                    || gift.boxCount() > 10000
                    || gift.recipe() == null
                    || gift.recipe().isEmpty()
                    || gift.recipe().size() > 30)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Choose 1–10,000 boxes and a valid assortment.");
            Box box =
                    catalogue(branchId, false).boxes().stream()
                            .filter(b -> b.id() == gift.boxId())
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "Packaging is unavailable."));
            long pieces = 0;
            Set<Long> seen = new HashSet<>();
            for (Recipe recipe : gift.recipe()) {
                if (recipe == null || recipe.pieces() < 1 || !seen.add(recipe.productId()))
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Each sweet needs a positive pieces-per-box quantity.");
                pieces += recipe.pieces();
                BigDecimal total = BigDecimal.valueOf((long) recipe.pieces() * gift.boxCount());
                if (items.stream()
                        .noneMatch(
                                i ->
                                        i.productId() == recipe.productId()
                                                && i.unit() == OccasionEnquiryService.Unit.PIECE
                                                && i.quantity().compareTo(total) == 0))
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Requested pieces must equal boxes multiplied by pieces per box.");
            }
            if (seen.size() != items.size()
                    || pieces > box.capacityPieces()
                    || date.isBefore(
                            LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")))
                                    .plusDays(box.leadDays())))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Check box capacity and packaging lead time. The manager will also review"
                                + " physical fit.");
            return new GiftSnapshot(
                    box,
                    gift.boxCount(),
                    List.copyOf(gift.recipe()),
                    box.price() == null
                            ? null
                            : box.price().multiply(BigDecimal.valueOf(gift.boxCount())),
                    null,
                    gift.includeSpoons());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionCatalogue.class,
                    "validateGift(long,LocalDate,List<OccasionEnquiryService.Item>,GiftRequest)");
        }
    }

    /**
     * Validates groups.
     *
     * @param branchId the branch id
     * @param date the date
     * @param items the items
     * @param groups the groups
     * @return the validate groups result
     */
    public List<PackedGroup> validateGroups(
            long branchId,
            LocalDate date,
            List<OccasionEnquiryService.Item> items,
            List<PackingGroup> groups) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionCatalogue.class,
                        "validateGroups(long,LocalDate,List<OccasionEnquiryService.Item>,List<PackingGroup>)");
        try {
            if (groups == null || groups.isEmpty()) return List.of();
            if (groups.size() > 30) badPacking("Use at most 30 packing groups.");
            var catalogue = catalogue(branchId, false);
            var totals = new HashMap<String, BigDecimal>();
            var result = new ArrayList<PackedGroup>();
            for (var group : groups) {
                if (group == null
                        || group.boxId() == null
                        || group.boxCount() < 1
                        || group.boxCount() > 10000)
                    badPacking("Choose a box and 1–10,000 packs per group.");
                var box =
                        catalogue.boxes().stream()
                                .filter(b -> b.id().equals(group.boxId()))
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new ResponseStatusException(
                                                        HttpStatus.BAD_REQUEST,
                                                        "Selected packaging is unavailable."));
                if (date.isBefore(
                        LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")))
                                .plusDays(box.leadDays())))
                    badPacking("Allow the selected packaging's preparation days.");
                String name = null;
                List<Recipe> recipe = List.of();
                if ("MIXED".equals(group.kind())) {
                    if (group.productId() != null
                            || group.totalGrams() != null
                            || group.packGrams() != null
                            || group.recipe() == null
                            || group.recipe().isEmpty()
                            || group.recipe().size() > 30)
                        badPacking("Choose pieces per mixed box.");
                    long count = 0;
                    var seen = new HashSet<Long>();
                    for (var line : group.recipe()) {
                        if (line == null
                                || line.pieces() < 1
                                || line.pieces() > 1000
                                || !seen.add(line.productId()))
                            badPacking("Choose each mixed-box item once with positive pieces.");
                        var item =
                                items.stream()
                                        .filter(
                                                i ->
                                                        i.productId() == line.productId()
                                                                && i.unit()
                                                                        == OccasionEnquiryService
                                                                                .Unit.PIECE)
                                        .findFirst()
                                        .orElseThrow(
                                                () ->
                                                        new ResponseStatusException(
                                                                HttpStatus.BAD_REQUEST,
                                                                "Mixed-box items must be requested"
                                                                        + " in pieces."));
                        count += line.pieces();
                        totals.merge(
                                item.productId() + ":PIECE",
                                BigDecimal.valueOf((long) line.pieces() * group.boxCount()),
                                BigDecimal::add);
                    }
                    if (count > box.capacityPieces())
                        badPacking(
                                "This assortment exceeds the box piece capacity. Choose a larger"
                                        + " box.");
                    recipe = List.copyOf(group.recipe());
                } else if ("WEIGHT".equals(group.kind())) {
                    if (group.recipe() != null && !group.recipe().isEmpty()
                            || group.productId() == null
                            || group.packGrams() == null
                            || !Set.of(250, 500, 1000).contains(group.packGrams())
                            || group.totalGrams() == null
                            || group.totalGrams()
                                            .compareTo(
                                                    BigDecimal.valueOf(
                                                            (long) group.boxCount()
                                                                    * group.packGrams()))
                                    != 0
                            || !group.packGrams().equals(box.capacityGrams()))
                        badPacking(
                                "Choose matching 1 kg, 500 g or 250 g packaging. Total kg must fill"
                                        + " whole packs.");
                    var item =
                            items.stream()
                                    .filter(
                                            i ->
                                                    i.productId() == group.productId()
                                                            && (i.unit()
                                                                            == OccasionEnquiryService
                                                                                    .Unit.GRAM
                                                                    || i.supplementalGrams() != null
                                                                            && i.supplementalGrams()
                                                                                            .signum()
                                                                                    > 0))
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    new ResponseStatusException(
                                                            HttpStatus.BAD_REQUEST,
                                                            "Weight packs must use a requested kg"
                                                                    + " item."));
                    name =
                            catalogue.sweets().stream()
                                    .filter(x -> x.id() == item.productId())
                                    .findFirst()
                                    .orElseThrow()
                                    .name();
                    totals.merge(item.productId() + ":GRAM", group.totalGrams(), BigDecimal::add);
                } else badPacking("Choose mixed pieces or weight packs.");
                result.add(
                        new PackedGroup(
                                result.size() + 1,
                                group.kind(),
                                box,
                                group.boxCount(),
                                recipe,
                                group.productId(),
                                name,
                                group.totalGrams(),
                                group.packGrams(),
                                group.includeSpoons(),
                                box.price() == null
                                        ? null
                                        : box.price()
                                                .multiply(BigDecimal.valueOf(group.boxCount()))));
            }
            for (var item : items) {
                BigDecimal grams =
                        item.unit() == OccasionEnquiryService.Unit.GRAM
                                ? item.quantity()
                                : item.supplementalGrams() == null
                                        ? BigDecimal.ZERO
                                        : item.supplementalGrams();
                BigDecimal pieces =
                        item.unit() == OccasionEnquiryService.Unit.PIECE
                                ? item.quantity()
                                : BigDecimal.ZERO;
                if (totals.getOrDefault(item.productId() + ":GRAM", BigDecimal.ZERO)
                                        .compareTo(grams)
                                > 0
                        || totals.getOrDefault(item.productId() + ":PIECE", BigDecimal.ZERO)
                                        .compareTo(pieces)
                                > 0)
                    badPacking(
                            "Packing groups exceed the requested quantity. Reduce a group or"
                                    + " increase the item quantity.");
            }
            return List.copyOf(result);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionCatalogue.class,
                    "validateGroups(long,LocalDate,List<OccasionEnquiryService.Item>,List<PackingGroup>)");
        }
    }

    /**
     * Bads packing.
     *
     * @param message the message
     */
    private static void badPacking(String message) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionCatalogue.class, "badPacking(String)");
        try {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionCatalogue.class, "badPacking(String)");
        }
    }
}
