package com.gokulsweets.restaurant.menu;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class MenuAvailabilityService {
    private final MenuCatalogService catalog;
    private final MenuServiceWindows windows;
    public record Item(long productId,boolean available,MenuServiceWindows.Status serviceAvailability) {}
    public record Availability(long revision,boolean serviceWindowsEnabled,List<Item> items) {}
    @Transactional(readOnly=true)
    public Availability get(long branchId) {
        for(int attempt=0;attempt<3;attempt++) {
            var snapshot=catalog.get(branchId);var live=windows.snapshot(branchId);
            var items=snapshot.categories().stream().flatMap(c->c.products().stream()).map(p->{
                var status=live.status(p.id());return new Item(p.id(),p.available()&&(status==null||status.available()),status);
            }).toList();
            if(catalog.revision()==snapshot.revision())return new Availability(snapshot.revision(),live.enabled(),items);
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Menu is updating. Please try again.");
    }
}
