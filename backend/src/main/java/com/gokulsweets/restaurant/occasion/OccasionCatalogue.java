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
                        boolean occasionOnly, boolean published, int leadDays, BigDecimal pieceGrams, Long categoryId, String categoryName, BigDecimal unitPrice, BigDecimal taxPercent) {
        public Sweet(long id,String name,String description,String imageUrl,String saleMode,boolean occasionOnly,boolean published,int leadDays,BigDecimal pieceGrams) {this(id,name,description,imageUrl,saleMode,occasionOnly,published,leadDays,pieceGrams,null,"Selection",null,null);}
    }
    public record Box(Long id, @NotBlank @Size(max=100) String name, @Size(max=1000) String imageUrl,
                      @NotBlank @Size(max=100) String dimensions, @NotBlank @Size(max=100) String material,
                      @Min(1) @Max(100) int compartments, @Min(1) @Max(1000) int capacityPieces,
                      @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal price,
                      @NotNull @Size(max=300) String branding, @Min(0) @Max(365) int leadDays, boolean published, @Size(max=6) List<@NotBlank @Size(max=1000) String> imageUrls, @Min(250) @Max(1000) Integer capacityGrams) {
        public Box(Long id,String name,String imageUrl,String dimensions,String material,int compartments,int capacityPieces,BigDecimal price,String branding,int leadDays,boolean published) {this(id,name,imageUrl,dimensions,material,compartments,capacityPieces,price,branding,leadDays,published,List.of(),null);}
        public Box(Long id,String name,String imageUrl,String dimensions,String material,int compartments,int capacityPieces,BigDecimal price,String branding,int leadDays,boolean published,List<String> imageUrls) {this(id,name,imageUrl,dimensions,material,compartments,capacityPieces,price,branding,leadDays,published,imageUrls,null);}
    }
    public record PackingGroup(String kind, Long boxId, int boxCount, List<Recipe> recipe,
                               Long productId, BigDecimal totalGrams, Integer packGrams, boolean includeSpoons) {}
    public record PackedGroup(int groupNumber, String kind, Box box, int boxCount, List<Recipe> recipe,
                              Long productId, String productName, BigDecimal totalGrams, Integer packGrams,
                              boolean includeSpoons, BigDecimal packagingEstimate) {}
    public record Branding(@NotNull @Size(max=100) String headline,@NotNull @Size(max=500) String description,@Size(max=1000) String imageUrl,boolean published) {}
    public record Catalogue(List<Sweet> sweets, List<Box> boxes, Branding branding) {public Catalogue(List<Sweet> sweets,List<Box> boxes) {this(sweets,boxes,null);}}
    private List<String> images(String json) {try {return mapper.readValue(json,new tools.jackson.core.type.TypeReference<List<String>>() {});} catch(Exception error){throw new IllegalStateException("Invalid packaging gallery",error);}}
    private String json(List<String> urls) {try{return mapper.writeValueAsString(urls==null?List.of():urls);}catch(Exception error){throw new IllegalArgumentException("Invalid gallery",error);}}
    private void photo(String url) {if(url!=null && !url.isBlank() && !url.startsWith("https://"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use an HTTPS photo.");}
    public record SweetSettings(boolean occasionOnly, boolean published, @Min(0) @Max(365) int leadDays,
                                @DecimalMin("0.001") @Digits(integer=6,fraction=3) BigDecimal pieceGrams) {}
    public record Recipe(long productId, int pieces) {}
    public record GiftRequest(long boxId, int boxCount, List<Recipe> recipe,boolean includeSpoons) {
        public GiftRequest(long boxId,int boxCount,List<Recipe> recipe) {this(boxId,boxCount,recipe,false);}
    }
    public record GiftSnapshot(Box box, int boxCount, List<Recipe> recipe, BigDecimal packagingEstimate, BigDecimal approvedPackagingTotal,boolean includeSpoons) {
        public GiftSnapshot(Box box,int boxCount,List<Recipe> recipe,BigDecimal packagingEstimate,BigDecimal approvedPackagingTotal) {this(box,boxCount,recipe,packagingEstimate,approvedPackagingTotal,false);}
    }
    void enabled() {if (!features.isOccasionEnquiries()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    @Transactional(readOnly=true)
    public Catalogue catalogue(long branchId, boolean admin) {
        enabled();
        if(!admin && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM branches WHERE id=? AND active AND operational)",Boolean.class,branchId)))throw new ResponseStatusException(HttpStatus.CONFLICT,"This branch is currently not operational.");
        var sweets = jdbc.query("""
            SELECT p.id,p.name,p.description,p.image_url,p.sale_mode,bp.occasion_only,bp.occasion_published,
                   bp.occasion_lead_days,bp.occasion_piece_grams,p.category_id,c.name,COALESCE(bp.price_override,p.base_price),tc.cgst_rate+tc.sgst_rate
            FROM branch_products bp JOIN products p ON p.id=bp.product_id JOIN branches b ON b.id=bp.branch_id JOIN categories c ON c.id=p.category_id JOIN tax_categories tc ON tc.id=p.tax_category_id AND tc.active
            WHERE bp.branch_id=? AND b.active AND p.active AND (? OR bp.occasion_published)
            ORDER BY bp.occasion_only DESC,bp.display_order,p.name
            """, (rs,n)->new Sweet(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
                rs.getBoolean(6),rs.getBoolean(7),rs.getInt(8),rs.getBigDecimal(9),rs.getLong(10),rs.getString(11),rs.getBigDecimal(12),rs.getBigDecimal(13)),branchId,admin);
        var boxes=jdbc.query("SELECT * FROM occasion_packaging WHERE branch_id=? AND (? OR published) ORDER BY id",
            (rs,n)->new Box(rs.getLong("id"),rs.getString("name"),rs.getString("image_url"),rs.getString("dimensions"),
                rs.getString("material"),rs.getInt("compartments"),rs.getInt("capacity_pieces"),rs.getBigDecimal("price"),
                rs.getString("branding"),rs.getInt("lead_days"),rs.getBoolean("published"),images(rs.getString("image_urls")),rs.getObject("capacity_grams",Integer.class)),branchId,admin);
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
        if(box.capacityGrams()!=null && !Set.of(250,500,1000).contains(box.capacityGrams()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose 250 g, 500 g or 1 kg food capacity.");
        if(box.imageUrl()!=null && !box.imageUrl().isBlank() && !box.imageUrl().startsWith("https://"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use an HTTPS URL for a real packaging photo.");
        if(box.imageUrls()!=null)box.imageUrls().forEach(this::photo);
        if(box.id()==null) {
            Long id=jdbc.queryForObject("""
                INSERT INTO occasion_packaging(branch_id,name,image_url,dimensions,material,compartments,capacity_pieces,price,branding,lead_days,published,image_urls,capacity_grams)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?) RETURNING id
                """,Long.class,branchId,box.name(),box.imageUrl(),box.dimensions(),box.material(),box.compartments(),box.capacityPieces(),box.price(),box.branding(),box.leadDays(),box.published(),json(box.imageUrls()),box.capacityGrams());
            return new Box(id,box.name(),box.imageUrl(),box.dimensions(),box.material(),box.compartments(),box.capacityPieces(),box.price(),box.branding(),box.leadDays(),box.published(),box.imageUrls(),box.capacityGrams());
        }
        if(jdbc.update("""
            UPDATE occasion_packaging SET name=?,image_url=?,dimensions=?,material=?,compartments=?,capacity_pieces=?,price=?,branding=?,lead_days=?,published=?,image_urls=?::jsonb,capacity_grams=? WHERE id=? AND branch_id=?
            """,box.name(),box.imageUrl(),box.dimensions(),box.material(),box.compartments(),box.capacityPieces(),box.price(),box.branding(),box.leadDays(),box.published(),json(box.imageUrls()),box.capacityGrams(),box.id(),branchId)!=1)
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
        if(seen.size()!=items.size() || pieces>box.capacityPieces()
            || date.isBefore(LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(box.leadDays())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Check box capacity and packaging lead time. The manager will also review physical fit.");
        return new GiftSnapshot(box,gift.boxCount(),List.copyOf(gift.recipe()),box.price()==null?null:box.price().multiply(BigDecimal.valueOf(gift.boxCount())),null,gift.includeSpoons());
    }
    public List<PackedGroup> validateGroups(long branchId,LocalDate date,List<OccasionEnquiryService.Item> items,List<PackingGroup> groups) {
        if(groups==null || groups.isEmpty())return List.of();
        if(groups.size()>30)badPacking("Use at most 30 packing groups.");
        var catalogue=catalogue(branchId,false);var totals=new HashMap<String,BigDecimal>();var result=new ArrayList<PackedGroup>();
        for(var group:groups) {
            if(group==null || group.boxId()==null || group.boxCount()<1 || group.boxCount()>10000)badPacking("Choose a box and 1–10,000 packs per group.");
            var box=catalogue.boxes().stream().filter(b->b.id().equals(group.boxId())).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selected packaging is unavailable."));
            if(date.isBefore(LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(box.leadDays())))badPacking("Allow the selected packaging's preparation days.");
            String name=null;List<Recipe> recipe=List.of();
            if("MIXED".equals(group.kind())) {
                if(group.productId()!=null || group.totalGrams()!=null || group.packGrams()!=null || group.recipe()==null || group.recipe().isEmpty() || group.recipe().size()>30)badPacking("Choose pieces per mixed box.");
                long count=0;var seen=new HashSet<Long>();
                for(var line:group.recipe()) {
                    if(line==null || line.pieces()<1 || line.pieces()>1000 || !seen.add(line.productId()))badPacking("Choose each mixed-box item once with positive pieces.");
                    var item=items.stream().filter(i->i.productId()==line.productId() && i.unit()==OccasionEnquiryService.Unit.PIECE).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mixed-box items must be requested in pieces."));
                    count+=line.pieces();totals.merge(item.productId()+":PIECE",BigDecimal.valueOf((long)line.pieces()*group.boxCount()),BigDecimal::add);
                }
                if(count>box.capacityPieces())badPacking("This assortment exceeds the box piece capacity. Choose a larger box.");
                recipe=List.copyOf(group.recipe());
            } else if("WEIGHT".equals(group.kind())) {
                if(group.recipe()!=null&&!group.recipe().isEmpty() || group.productId()==null || group.packGrams()==null || !Set.of(250,500,1000).contains(group.packGrams()) || group.totalGrams()==null || group.totalGrams().compareTo(BigDecimal.valueOf((long)group.boxCount()*group.packGrams()))!=0 || !group.packGrams().equals(box.capacityGrams()))badPacking("Choose matching 1 kg, 500 g or 250 g packaging. Total kg must fill whole packs.");
                var item=items.stream().filter(i->i.productId()==group.productId() && (i.unit()==OccasionEnquiryService.Unit.GRAM || i.supplementalGrams()!=null && i.supplementalGrams().signum()>0)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Weight packs must use a requested kg item."));
                name=catalogue.sweets().stream().filter(x->x.id()==item.productId()).findFirst().orElseThrow().name();
                totals.merge(item.productId()+":GRAM",group.totalGrams(),BigDecimal::add);
            } else badPacking("Choose mixed pieces or weight packs.");
            result.add(new PackedGroup(result.size()+1,group.kind(),box,group.boxCount(),recipe,group.productId(),name,group.totalGrams(),group.packGrams(),group.includeSpoons(),box.price()==null?null:box.price().multiply(BigDecimal.valueOf(group.boxCount()))));
        }
        for(var item:items) {
            BigDecimal grams=item.unit()==OccasionEnquiryService.Unit.GRAM?item.quantity():item.supplementalGrams()==null?BigDecimal.ZERO:item.supplementalGrams();
            BigDecimal pieces=item.unit()==OccasionEnquiryService.Unit.PIECE?item.quantity():BigDecimal.ZERO;
            if(totals.getOrDefault(item.productId()+":GRAM",BigDecimal.ZERO).compareTo(grams)>0 || totals.getOrDefault(item.productId()+":PIECE",BigDecimal.ZERO).compareTo(pieces)>0)badPacking("Packing groups exceed the requested quantity. Reduce a group or increase the item quantity.");
        }
        return List.copyOf(result);
    }
    private static void badPacking(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}

}
