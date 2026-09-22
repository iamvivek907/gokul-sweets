package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchActiveRequest;
import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchCreateRequest;
import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchResponse;
import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/branches")
@RequiredArgsConstructor
public class AdminBranchController {

    private final AdminBranchService
            adminBranchService;


    /*
     * =========================================================
     * LIST
     * =========================================================
     */

    @GetMapping
    @PreAuthorize(
            "hasAuthority('BRANCH_MANAGE')"
    )
    public ResponseEntity<List<AdminBranchResponse>>
    getBranches() {

        return ResponseEntity.ok(
                adminBranchService
                        .getBranches()
        );
    }


    /*
     * =========================================================
     * DETAIL
     * =========================================================
     */

    @GetMapping("/{branchId}")
    @PreAuthorize(
            "hasAuthority('BRANCH_MANAGE')"
    )
    public ResponseEntity<AdminBranchResponse>
    getBranch(
            @PathVariable
            Long branchId
    ) {

        return ResponseEntity.ok(
                adminBranchService
                        .getBranch(
                                branchId
                        )
        );
    }


    /*
     * =========================================================
     * CREATE
     * =========================================================
     */

    @PostMapping
    @PreAuthorize(
            "hasAuthority('BRANCH_MANAGE')"
    )
    public ResponseEntity<AdminBranchResponse>
    createBranch(

            @Valid
            @RequestBody
            AdminBranchCreateRequest request
    ) {

        AdminBranchResponse created =
                adminBranchService
                        .createBranch(
                                request
                        );


        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        created
                );
    }


    /*
     * =========================================================
     * UPDATE
     * =========================================================
     */

    @PutMapping("/{branchId}")
    @PreAuthorize(
            "hasAuthority('BRANCH_MANAGE')"
    )
    public ResponseEntity<AdminBranchResponse>
    updateBranch(

            @PathVariable
            Long branchId,

            @Valid
            @RequestBody
            AdminBranchUpdateRequest request
    ) {

        return ResponseEntity.ok(
                adminBranchService
                        .updateBranch(
                                branchId,
                                request
                        )
        );
    }


    /*
     * =========================================================
     * ACTIVE STATUS
     * =========================================================
     */

    @PatchMapping("/{branchId}/active")
    @PreAuthorize(
            "hasAuthority('BRANCH_MANAGE')"
    )
    public ResponseEntity<AdminBranchResponse>
    updateActiveStatus(

            @PathVariable
            Long branchId,

            @Valid
            @RequestBody
            AdminBranchActiveRequest request
    ) {

        return ResponseEntity.ok(
                adminBranchService
                        .updateActiveStatus(
                                branchId,
                                request
                        )
        );
    }
}