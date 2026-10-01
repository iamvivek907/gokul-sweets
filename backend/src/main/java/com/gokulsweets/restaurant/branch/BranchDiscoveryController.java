package com.gokulsweets.restaurant.branch;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/branches/{branchId}/discovery")
public class BranchDiscoveryController {
 private final BranchDiscoveryService discovery;
 @GetMapping public BranchDiscoveryService.Discovery get(@PathVariable long branchId){return discovery.get(branchId);}
}
