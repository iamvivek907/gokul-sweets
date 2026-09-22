package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.branch.dto.BranchResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public List<BranchResponse> getActiveBranches() {
        return branchService.getActiveBranches();
    }

    @GetMapping("/{id}")
    public BranchResponse getBranch(@PathVariable Long id) {
        return branchService.getBranch(id);
    }
}