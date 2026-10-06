package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.product.ProductSaleMode;
import com.gokulsweets.restaurant.menu.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Immutable, bounded snapshots. Only catalog data lives here; stock reservations never do. */
@Service @RequiredArgsConstructor
public class MenuCatalogService {
    private final JdbcTemplate jdbc;
    private final Map<Long,Catalog> cache = new LinkedHashMap<>(16, .75f, true);
    public record Catalog(String revision, List<MenuCategoryResponse> categories) {}
    public String revision() { return jdbc.queryForObject("SELECT token::text FROM menu_catalog_revision WHERE id=true", String.class); }
    public void requireBranch(long id) {
        var states=jdbc.query("SELECT active,operational FROM branches WHERE id=?",(r,n)->new boolean[]{r.getBoolean(1),r.getBoolean(2)},id);
        if(states.isEmpty())throw new IllegalArgumentException("Selected branch does not exist.");
        if(!states.getFirst()[0])throw new IllegalArgumentException("Selected branch is currently unavailable.");
        if(!states.getFirst()[1])throw new IllegalArgumentException("This branch is currently not operational.");
    }
    @Transactional(readOnly=true)
    public Catalog get(long branchId) {
        requireBranch(branchId);
        // Never publish uncommitted admin/import data into a shared process cache.
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly())return build(branchId,revision());
        synchronized(cache) {
            for(int attempt=0;attempt<3;attempt++) {
                String revision=revision(); var existing=cache.get(branchId);
                if(existing!=null && existing.revision().equals(revision))return existing;
                var snapshot=build(branchId,revision);
                // Do not publish a snapshot assembled across a committed catalog change.
                if(!revision().equals(revision))continue;
                cache.put(branchId,snapshot);
                while(cache.size()>16 || cache.values().stream().mapToLong(this::estimatedBytes).sum()>8*1024*1024)cache.remove(cache.keySet().iterator().next());
                return snapshot;
            }
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Menu is updating. Please try again.");
    }
    private long estimatedBytes(Catalog value) {
        return value.categories().stream().mapToLong(c->256+c.products().stream().mapToLong(p->512+2L*(length(p.name())+length(p.description())+length(p.imageUrl())+length(p.categoryName()))).sum()).sum();
    }
    private int length(String value){return value==null?0:value.length();}
    private Catalog build(long branchId,String revision) {
        var categories=new LinkedHashMap<Long,MenuCategoryResponse>();
        var grouped=new LinkedHashMap<Long,List<MenuProductResponse>>();
        // Scalar JDBC reads bypass Hibernate's first-level cache on every retry.
        // The revision bracket rejects any concurrent committed edit during this query.
        jdbc.query("""
                SELECT c.id AS category_id,c.name AS category_name,c.description AS category_description,
                       c.display_order AS category_order,p.id,p.name,p.description,
                       COALESCE(bp.price_override,p.base_price) AS price,p.image_url,bp.available,
                       p.sale_mode,p.minimum_weight_grams,p.weight_step_grams
                FROM branch_products bp JOIN products p ON p.id=bp.product_id
                JOIN categories c ON c.id=p.category_id
                WHERE bp.branch_id=? AND p.active AND c.active AND NOT bp.occasion_only
                ORDER BY c.display_order,bp.display_order,p.name
                """, (org.springframework.jdbc.core.RowCallbackHandler) r -> {
            long id=r.getLong("category_id");
            String name=r.getString("category_name");
            categories.putIfAbsent(id,new MenuCategoryResponse(id,name,r.getString("category_description"),r.getInt("category_order"),List.of()));
            grouped.computeIfAbsent(id,key->new ArrayList<>()).add(new MenuProductResponse(
                    r.getLong("id"),id,name,r.getString("name"),r.getString("description"),
                    r.getBigDecimal("price"),r.getString("image_url"),r.getBoolean("available"),
                    ProductSaleMode.valueOf(r.getString("sale_mode")),
                    r.getObject("minimum_weight_grams",Integer.class),r.getObject("weight_step_grams",Integer.class)));
        },branchId);
        return new Catalog(revision,categories.values().stream().map(c->new MenuCategoryResponse(c.id(),c.name(),c.description(),c.displayOrder(),List.copyOf(grouped.get(c.id())))).toList());
    }
}
