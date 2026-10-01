package com.gokulsweets.restaurant.menu;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class MobileMenuOptionsController {
 private final MobileMenuOptionsService options;
 @GetMapping("/api/menu/portion-groups") public ResponseEntity<MobileMenuOptionsService.Snapshot> read(@RequestParam long branchId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(options.publicRead(branchId));}
 @GetMapping("/api/admin/branches/{branchId}/menu/portion-groups") public MobileMenuOptionsService.Snapshot adminRead(@PathVariable long branchId){return options.adminRead(branchId);}
 @PutMapping("/api/admin/branches/{branchId}/menu/portion-groups") public MobileMenuOptionsService.Snapshot save(@PathVariable long branchId,@Valid @RequestBody MobileMenuOptionsService.Input input){return options.save(branchId,input);}
}
