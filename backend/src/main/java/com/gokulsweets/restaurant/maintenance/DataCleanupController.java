package com.gokulsweets.restaurant.maintenance;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/data-cleanup")
@RequiredArgsConstructor
public class DataCleanupController {
    private final StaffAuthorizationService staff;
    private final DataCleanupService cleanup;
    private long owner() {
        var actor=staff.getCurrentStaff();
        if(!"OWNER_ADMIN".equals(actor.getRole().getName()))throw new AccessDeniedException("Only the owner may configure or run data cleanup.");
        return actor.getId();
    }
    @GetMapping public ResponseEntity<DataCleanupService.View> view() {owner();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cleanup.view());}
    @PutMapping public DataCleanupService.View save(@RequestBody DataCleanupService.Config config) {return cleanup.save(config,owner());}
    @PostMapping("/preview") public DataCleanupService.Preview preview() {owner();return cleanup.preview();}
    public record RunInput(long revision) {}
    @PostMapping("/run") public DataCleanupService.View run(@RequestBody RunInput input) {return cleanup.run(owner(),input.revision());}
}
