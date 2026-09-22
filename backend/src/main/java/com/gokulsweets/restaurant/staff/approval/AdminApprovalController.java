package com.gokulsweets.restaurant.staff.approval;

import com.gokulsweets.restaurant.staff.approval.dto.ApprovalActionRequest;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalCountsResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalRequestDetailResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalRequestResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalOptionsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/approvals")
@RequiredArgsConstructor
public class AdminApprovalController {

    private final AdminApprovalService
            adminApprovalService;


    @GetMapping
    @PreAuthorize(
            "hasAuthority('APPROVAL_VIEW')"
    )
    public ResponseEntity<Page<ApprovalRequestResponse>>
    getApprovals(

            @RequestParam(required = false)
            Long branchId,

            @RequestParam(required = false)
            ApprovalRequestType type,

            @RequestParam(required = false)
            ApprovalRequestStatus status,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "25")
            int size
    ) {

        return ResponseEntity.ok(
                adminApprovalService
                        .getApprovals(
                                branchId,
                                type,
                                status,
                                page,
                                size
                        )
        );
    }


    @GetMapping("/counts")
    @PreAuthorize(
            "hasAuthority('APPROVAL_VIEW')"
    )
    public ResponseEntity<ApprovalCountsResponse>
    getCounts(

            @RequestParam(required = false)
            Long branchId,

            @RequestParam(required = false)
            ApprovalRequestType type
    ) {

        return ResponseEntity.ok(
                adminApprovalService
                        .getCounts(
                                branchId,
                                type
                        )
        );
    }


    @GetMapping("/options")
    @PreAuthorize(
            "hasAuthority('APPROVAL_VIEW')"
    )
    public ResponseEntity<ApprovalOptionsResponse>
    getOptions() {

        return ResponseEntity.ok(
                adminApprovalService
                        .getOptions()
        );
    }


    @GetMapping("/{approvalRequestId}")
    @PreAuthorize(
            "hasAuthority('APPROVAL_VIEW')"
    )
    public ResponseEntity<ApprovalRequestDetailResponse>
    getApproval(

            @PathVariable
            Long approvalRequestId
    ) {

        return ResponseEntity.ok(
                adminApprovalService
                        .getApproval(
                                approvalRequestId
                        )
        );
    }


    @PostMapping("/{approvalRequestId}/approve")
    @PreAuthorize(
            "hasAuthority('APPROVAL_MANAGE')"
    )
    public ResponseEntity<ApprovalRequestResponse>
    approve(

            @PathVariable
            Long approvalRequestId,

            @Valid
            @RequestBody
            ApprovalActionRequest request
    ) {

        return ResponseEntity.ok(
                adminApprovalService
                        .approve(
                                approvalRequestId,
                                request
                        )
        );
    }


    @PostMapping("/{approvalRequestId}/reject")
    @PreAuthorize(
            "hasAuthority('APPROVAL_MANAGE')"
    )
    public ResponseEntity<ApprovalRequestResponse>
    reject(

            @PathVariable
            Long approvalRequestId,

            @Valid
            @RequestBody
            ApprovalActionRequest request
    ) {

        return ResponseEntity.ok(
                adminApprovalService
                        .reject(
                                approvalRequestId,
                                request
                        )
        );
    }


    @PostMapping("/{approvalRequestId}/send-back")
    @PreAuthorize(
            "hasAuthority('APPROVAL_MANAGE')"
    )
    public ResponseEntity<ApprovalRequestResponse>
    sendBack(

            @PathVariable
            Long approvalRequestId,

            @Valid
            @RequestBody
            ApprovalActionRequest request
    ) {

        return ResponseEntity.ok(
                adminApprovalService
                        .sendBack(
                                approvalRequestId,
                                request
                        )
        );
    }
}
