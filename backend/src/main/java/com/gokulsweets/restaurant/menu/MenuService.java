package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.menu.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Legacy combined endpoint remains compatible; new clients refresh only the availability overlay. */
@Service @RequiredArgsConstructor
public class MenuService {
    private final MenuCatalogService catalog;
    private final MenuAvailabilityService availability;
    @Transactional(readOnly=true)
    public List<MenuCategoryResponse> getMenu(Long branchId) {
        if(branchId==null)throw new IllegalArgumentException("Branch ID is required.");
        for(int attempt=0;attempt<3;attempt++) {
            var snapshot=catalog.get(branchId);var live=availability.get(branchId);
            if(snapshot.revision()!=live.revision())continue;
            var states=new HashMap<Long,MenuAvailabilityService.Item>();live.items().forEach(i->states.put(i.productId(),i));
            return snapshot.categories().stream().map(c->new MenuCategoryResponse(c.id(),c.name(),c.description(),c.displayOrder(),c.products().stream().filter(p->live.serviceWindowsEnabled()||states.get(p.id()).available()).map(p->{
                var state=states.get(p.id());return new MenuProductResponse(p.id(),p.categoryId(),p.categoryName(),p.name(),p.description(),p.price(),p.imageUrl(),state.available(),p.saleMode(),p.minimumWeightGrams(),p.weightStepGrams(),state.serviceAvailability());
            }).toList())).filter(c->!c.products().isEmpty()).toList();
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Menu is updating. Please try again.");
    }
}
