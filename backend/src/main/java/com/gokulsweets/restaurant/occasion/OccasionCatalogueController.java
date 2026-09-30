package com.gokulsweets.restaurant.occasion;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
public class OccasionCatalogueController {
 private final OccasionCatalogue catalogue;
 private final StaffAuthorizationService staff;
 @GetMapping("/api/branches/{branchId}/occasion-catalogue")
 public OccasionCatalogue.Catalogue customer(@PathVariable long branchId) {return catalogue.catalogue(branchId,false);}
 @GetMapping("/api/admin/branches/{branchId}/occasion-catalogue")
 @PreAuthorize("hasAuthority('MENU_MANAGE')")
 public OccasionCatalogue.Catalogue admin(@PathVariable long branchId) {staff.requireBranchAccess(branchId);return catalogue.catalogue(branchId,true);}
 @PutMapping("/api/admin/branches/{branchId}/occasion-catalogue/sweets/{productId}")
 @PreAuthorize("hasAuthority('MENU_MANAGE')")
 public void sweet(@PathVariable long branchId,@PathVariable long productId,@Valid @RequestBody OccasionCatalogue.SweetSettings input) {staff.requireBranchAccess(branchId);catalogue.configureSweet(branchId,productId,input);}
 @PostMapping("/api/admin/branches/{branchId}/occasion-catalogue/boxes")
 @PreAuthorize("hasAuthority('MENU_MANAGE')")
 public OccasionCatalogue.Box box(@PathVariable long branchId,@Valid @RequestBody OccasionCatalogue.Box input) {staff.requireBranchAccess(branchId);return catalogue.saveBox(branchId,input);}
}
