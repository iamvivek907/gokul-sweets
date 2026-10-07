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

 @GetMapping("/api/admin/branches/{branchId}/menu/workspace/groups") public MobileMenuOptionsService.GroupPage page(@PathVariable long branchId,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return options.page(branchId,search,page);}
 public record One(@jakarta.validation.constraints.Min(0) long version,@jakarta.validation.constraints.NotNull @Valid MobileMenuOptionsService.Group group) {}
 @PutMapping("/api/admin/branches/{branchId}/menu/workspace/groups") public java.util.Map<String,Long> one(@PathVariable long branchId,@Valid @RequestBody One input){return java.util.Map.of("version",options.saveOne(branchId,input.version(),input.group(),false));}
 @DeleteMapping("/api/admin/branches/{branchId}/menu/workspace/groups/{key}") public void remove(@PathVariable long branchId,@PathVariable String key,@RequestParam long version){if(!key.matches("[A-Za-z0-9-]{1,40}"))throw new IllegalArgumentException("Invalid group key.");options.saveOne(branchId,version,new MobileMenuOptionsService.Group(key,"",java.util.List.of()),true);}
 @GetMapping("/api/admin/branches/{branchId}/menu/workspace/groups/product/{id}") public MobileMenuOptionsService.GroupMatch forProduct(@PathVariable long branchId,@PathVariable long id){return options.forProduct(branchId,id);}
}
