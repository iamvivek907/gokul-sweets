package com.gokulsweets.restaurant.branch;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branchId}/offerings")
public class BranchOfferingsController {
 private final BranchOfferingsService service;
 @GetMapping public BranchOfferingsService.Snapshot get(@PathVariable long branchId){return service.admin(branchId);}
 @PutMapping public BranchOfferingsService.Snapshot save(@PathVariable long branchId,@RequestHeader("If-Match") long version,@Valid @RequestBody BranchOfferingsService.Input input){return service.save(branchId,input,version);}
 @PostMapping("/publish") public BranchOfferingsService.Snapshot publish(@PathVariable long branchId,@RequestHeader("If-Match") long version){return service.publish(branchId,version);}
}
