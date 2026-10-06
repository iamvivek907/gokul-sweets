package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.branchproduct.BranchProductRepository;
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
    private final BranchProductRepository products;
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
        for(var bp:products.findCatalog(branchId)) {
            var p=bp.getProduct(); var c=p.getCategory();
            if(!p.isActive()||!c.isActive()||bp.isOccasionOnly())continue;
            categories.putIfAbsent(c.getId(),new MenuCategoryResponse(c.getId(),c.getName(),c.getDescription(),c.getDisplayOrder(),List.of()));
            grouped.computeIfAbsent(c.getId(),id->new ArrayList<>()).add(new MenuProductResponse(p.getId(),c.getId(),c.getName(),p.getName(),p.getDescription(),bp.getPriceOverride()!=null?bp.getPriceOverride():p.getBasePrice(),p.getImageUrl(),bp.isAvailable(),p.getSaleMode(),p.getMinimumWeightGrams(),p.getWeightStepGrams()));
        }
        return new Catalog(revision,categories.values().stream().map(c->new MenuCategoryResponse(c.id(),c.name(),c.description(),c.displayOrder(),List.copyOf(grouped.get(c.id())))).toList());
    }
}
