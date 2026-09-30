package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OccasionCatalogue {
    private final JdbcTemplate jdbc;
    private final EnhancementProperties features;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
    public record Sweet(long id, String name, String description, String imageUrl, String saleMode,
                        boolean occasionOnly, boolean published, int leadDays, BigDecimal pieceGrams, Long categoryId, String categoryName) {
        public Sweet(long id,String name,String description,String imageUrl,String saleMode,boolean occasionOnly,boolean published,int leadDays,BigDecimal pieceGrams) {this(id,name,description,imageUrl,saleMode,occasionOnly,published,leadDays,pieceGrams,null,"Selection");}
    }
    public record Box(Long id, @NotBlank @Size(max=100) String name, @Size(max=1000) String imageUrl,
                      @NotBlank @Size(max=100) String dimensions, @NotBlank @Size(max=100) String material,
                      @Min(1) @Max(100) int compartments, @Min(1) @Max(1000) int capacityPieces,
                      @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal price,
                      @NotNull @Size(max=300) String branding, @Min(0) @Max(365) int leadDays, boolean published, @Size(max=6) List<@NotBlank @Size(max=1000) String> imageUrls) {
        public Box(Long id,String name,String imageUrl,String dimensions,String material,int compartments,int capacityPieces,BigDecimal price,String branding,int leadDays,boolean published) {this(id,name,imageUrl,dimensions,material,compartments,capacityPieces,price,branding,leadDays,published,List.of());}
    }
    public record Branding(@NotNull @Size(max=100) String headline,@NotNull @Size(max=500) String description,@Size(max=1000) String imageUrl,boolean published) {}
    public record Catalogue(List<Sweet> sweets, List<Box> boxes, Branding branding) {public Catalogue(List<Sweet> sweets,List<Box> boxes) {this(sweets,boxes,null);}}
    private List<String> images(String json) {try {return mapper.readValue(json,new tools.jackson.core.type.TypeReference<List<String>>() {});} catch(Exception error){throw new IllegalStateException("Invalid packaging gallery",error);}}
    private String json(List<String> urls) {try{return mapper.writeValueAsString(urls==null?List.of():urls);}catch(Exception error){throw new IllegalArgumentException("Invalid gallery",error);}}
    private void photo(String url) {if(url!=null && !url.isBlank() && !url.startsWith("https://"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use an HTTPS photo.");}
    public record SweetSettings(boolean occasionOnly, boolean published, @Min(0) @Max(365) int leadDays,
                                @DecimalMin("0.001") @Digits(integer=6,fraction=3) BigDecimal pieceGrams) {}
    public record Recipe(long productId, int pieces) {}
    public record GiftRequest(long boxId, int boxCount, List<Recipe> recipe) {}
    public record GiftSnapshot(Box box, int boxCount, List<Recipe> recipe, BigDecimal packagingEstimate, BigDecimal approvedPackagingTotal) {}
    void enabled() {if (!features.isOccasionEnquiries()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    @Transactional(readOnly=true)
    public Catalogue catalogue(long branchId, boolean admin) {
        enabled();
        var sweets = jdbc.query("""
            SELECT p.id,p.name,p.description,p.image_url,p.sale_mode,bp.occasion_only,bp.occasion_published,
                   bp.occasion_lead_days,bp.occasion_piece_grams,p.category_id,c.name
            FROM branch_products bp JOIN products p ON p.id=bp.product_id JOIN branches b ON b.id=bp.branch_id JOIN categories c ON c.id=p.category_id
            WHERE bp.branch_id=? AND b.active AND p.active AND (? OR bp.occasion_published)
            ORDER BY bp.occasion_only DESC,bp.display_order,p.name
            """, (rs,n)->new Sweet(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
                rs.getBoolean(6),rs.getBoolean(7),rs.getInt(8),rs.getBigDecimal(9),rs.getLong(10),rs.getString(11)),branchId,admin);
        var boxes=jdbc.query("SELECT * FROM occasion_packaging WHERE branch_id=? AND (? OR published) ORDER BY id",
            (rs,n)->new Box(rs.getLong("id"),rs.getString("name"),rs.getString("image_url"),rs.getString("dimensions"),
                rs.getString("material"),rs.getInt("compartments"),rs.getInt("capacity_pieces"),rs.getBigDecimal("price"),
                rs.getString("branding"),rs.getInt("lead_days"),rs.getBoolean("published"),images(rs.getString("image_urls"))),branchId,admin);
        var branding=jdbc.query("SELECT * FROM occasion_branding WHERE branch_id=? AND (? OR published)",(rs,n)->new Branding(rs.getString("headline"),rs.getString("description"),rs.getString("image_url"),rs.getBoolean("published")),branchId,admin);
        return new Catalogue(sweets,boxes,branding.isEmpty()?null:branding.getFirst());
    }
    @Transactional
    public void configureSweet(long branchId,long productId,SweetSettings input) {
        enabled();
        if(jdbc.update("UPDATE branch_products SET occasion_only=?,occasion_published=?,occasion_lead_days=?,occasion_piece_grams=? WHERE branch_id=? AND product_id=?",
            input.occasionOnly(),input.published(),input.leadDays(),input.pieceGrams(),branchId,productId)!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    @Transactional
    public Box saveBox(long branchId,Box box) {
        enabled();
        if(box.imageUrl()!=null && !box.imageUrl().isBlank() && !box.imageUrl().startsWith("https://"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use an HTTPS URL for a real packaging photo.");
        if(box.imageUrls()!=null)box.imageUrls().forEach(this::photo);
        if(box.id()==null) {
            Long id=jdbc.queryForObject("""
                INSERT INTO occasion_packaging(branch_id,name,image_url,dimensions,material,compartments,capacity_pieces,price,branding,lead_days,published,image_urls)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?::jsonb) RETURNING id
                """,Long.class,branchId,box.name(),box.imageUrl(),box.dimensions(),box.material(),box.compartments(),box.capacityPieces(),box.price(),box.branding(),box.leadDays(),box.published(),json(box.imageUrls()));
            return new Box(id,box.name(),box.imageUrl(),box.dimensions(),box.material(),box.compartments(),box.capacityPieces(),box.price(),box.branding(),box.leadDays(),box.published(),box.imageUrls());
        }
        if(jdbc.update("""
            UPDATE occasion_packaging SET name=?,image_url=?,dimensions=?,material=?,compartments=?,capacity_pieces=?,price=?,branding=?,lead_days=?,published=?,image_urls=?::jsonb WHERE id=? AND branch_id=?
            """,box.name(),box.imageUrl(),box.dimensions(),box.material(),box.compartments(),box.capacityPieces(),box.price(),box.branding(),box.leadDays(),box.published(),json(box.imageUrls()),box.id(),branchId)!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return box;
    }
    @Transactional
    public void saveBranding(long branchId,Branding input) {
        enabled();photo(input.imageUrl());
        jdbc.update("INSERT INTO occasion_branding(branch_id,headline,description,image_url,published) VALUES (?,?,?,?,?) ON CONFLICT(branch_id) DO UPDATE SET headline=EXCLUDED.headline,description=EXCLUDED.description,image_url=EXCLUDED.image_url,published=EXCLUDED.published",branchId,input.headline(),input.description(),input.imageUrl(),input.published());
    }
    public GiftSnapshot validateGift(long branchId,LocalDate date,List<OccasionEnquiryService.Item> items,GiftRequest gift) {
        if(gift==null)return null;
        if(gift.boxCount()<1 || gift.boxCount()>10000 || gift.recipe()==null || gift.recipe().isEmpty() || gift.recipe().size()>30)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose 1–10,000 boxes and a valid assortment.");
        Box box=catalogue(branchId,false).boxes().stream().filter(b->b.id()==gift.boxId()).findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Packaging is unavailable."));
        long pieces=0;Set<Long> seen=new HashSet<>();
        for(Recipe recipe:gift.recipe()) {
            if(recipe==null || recipe.pieces()<1 || !seen.add(recipe.productId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Each sweet needs a positive pieces-per-box quantity.");
            pieces+=recipe.pieces();
            BigDecimal total=BigDecimal.valueOf((long)recipe.pieces()*gift.boxCount());
            if(items.stream().noneMatch(i->i.productId()==recipe.productId() && i.unit()==OccasionEnquiryService.Unit.PIECE && i.quantity().compareTo(total)==0))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Requested pieces must equal boxes multiplied by pieces per box.");
        }
        if(seen.size()!=items.size() || pieces>box.capacityPieces() || seen.size()>box.compartments()
            || date.isBefore(LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(box.leadDays())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Check box capacity, compartments and packaging lead time. The manager will also review physical fit.");
        return new GiftSnapshot(box,gift.boxCount(),List.copyOf(gift.recipe()),box.price()==null?null:box.price().multiply(BigDecimal.valueOf(gift.boxCount())),null);
    }
}
