package com.gokulsweets.restaurant.menu;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class MenuAvailabilityService {
    private final MenuCatalogService catalog;
    private final MenuServiceWindows windows;
    private final Clock inventoryClock;
    private final Map<Long,Cached> cache=new LinkedHashMap<>(16,.75f,true);
    public record Item(long productId,boolean available,MenuServiceWindows.Status serviceAvailability) {}
    public record Availability(String revision,boolean serviceWindowsEnabled,List<Item> items,Instant observedAt) {
        public Availability(String revision,boolean serviceWindowsEnabled,List<Item> items){this(revision,serviceWindowsEnabled,items,null);}
    }
    private record Cached(Instant evaluatedAt,Instant until,Availability value) {}
    @Transactional(readOnly=true)
    public Availability get(long branchId) {
        // Menu flags are advisory. Never cache dated quantities, holds, or checkout acceptance.
        boolean publish=TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        synchronized(cache) {
            for(int attempt=0;attempt<3;attempt++) {
                var snapshot=catalog.get(branchId);Instant now=inventoryClock.instant();var cached=cache.get(branchId);
                if(publish&&cached!=null&&cached.value().revision().equals(snapshot.revision())&&!now.isBefore(cached.evaluatedAt())&&now.isBefore(cached.until()))return new Availability(cached.value().revision(),cached.value().serviceWindowsEnabled(),cached.value().items(),now);
                var live=windows.pickupSnapshot(branchId,null);
                var items=snapshot.categories().stream().flatMap(c->c.products().stream()).map(p->{
                    var status=live.status(p.id());return new Item(p.id(),p.available()&&(status==null||status.available()),status);
                }).toList();
                if(!catalog.revision().equals(snapshot.revision()))continue;
                var result=new Availability(snapshot.revision(),live.enabled(),items,now);
                if(publish) {
                    Instant until=now.plusSeconds(1);
                    for(var item:items)if(item.serviceAvailability()!=null&&item.serviceAvailability().nextChangeAt()!=null&&item.serviceAvailability().nextChangeAt().isBefore(until))until=item.serviceAvailability().nextChangeAt();
                    cache.put(branchId,new Cached(now,until,result));
                    while(cache.size()>16||cache.values().stream().mapToLong(c->256L+256L*c.value().items().size()).sum()>4*1024*1024)cache.remove(cache.keySet().iterator().next());
                }
                return result;
            }
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Menu is updating. Please try again.");
    }
}
