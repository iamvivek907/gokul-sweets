package com.gokulsweets.restaurant.branch;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/branches/{branchId}/operational") @RequiredArgsConstructor
public class AdminBranchOperationsController {
 private final BranchOperations operations;
 @GetMapping public BranchOperations.Status get(@PathVariable long branchId){return operations.get(branchId);}
 @PutMapping public BranchOperations.Status set(@PathVariable long branchId,@RequestBody BranchOperations.Status input){return operations.set(branchId,input);}
}
