package com.gokulsweets.restaurant.staff.approval;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalActionRequest;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalCountsResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalOptionsResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalRequestDetailResponse;
import com.gokulsweets.restaurant.staff.approval.dto.ApprovalRequestResponse;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for admin approval operations. */
@RestController
@RequestMapping("/api/admin/approvals")
@RequiredArgsConstructor
public class AdminApprovalController {

    private final AdminApprovalService adminApprovalService;

    /**
     * Returns approvals.
     *
     * @param branchId the branch id
     * @param type the type
     * @param status the status
     * @param page the page
     * @param size the size
     * @return the get approvals result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('APPROVAL_VIEW')")
    public ResponseEntity<Page<ApprovalRequestResponse>> getApprovals(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) ApprovalRequestType type,
            @RequestParam(required = false) ApprovalRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalController.class,
                        "getApprovals(Long,ApprovalRequestType,ApprovalRequestStatus,int,int)");
        try {
            return ResponseEntity.ok(
                    adminApprovalService.getApprovals(branchId, type, status, page, size));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalController.class,
                    "getApprovals(Long,ApprovalRequestType,ApprovalRequestStatus,int,int)");
        }
    }

    /**
     * Returns counts.
     *
     * @param branchId the branch id
     * @param type the type
     * @return the get counts result
     */
    @GetMapping("/counts")
    @PreAuthorize("hasAuthority('APPROVAL_VIEW')")
    public ResponseEntity<ApprovalCountsResponse> getCounts(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) ApprovalRequestType type) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalController.class, "getCounts(Long,ApprovalRequestType)");
        try {
            return ResponseEntity.ok(adminApprovalService.getCounts(branchId, type));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalController.class,
                    "getCounts(Long,ApprovalRequestType)");
        }
    }

    /**
     * Returns options.
     *
     * @return the get options result
     */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('APPROVAL_VIEW')")
    public ResponseEntity<ApprovalOptionsResponse> getOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminApprovalController.class, "getOptions()");
        try {
            return ResponseEntity.ok(adminApprovalService.getOptions());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminApprovalController.class, "getOptions()");
        }
    }

    /**
     * Returns approval.
     *
     * @param approvalRequestId the approval request id
     * @return the get approval result
     */
    @GetMapping("/{approvalRequestId}")
    @PreAuthorize("hasAuthority('APPROVAL_VIEW')")
    public ResponseEntity<ApprovalRequestDetailResponse> getApproval(
            @PathVariable Long approvalRequestId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminApprovalController.class, "getApproval(Long)");
        try {
            return ResponseEntity.ok(adminApprovalService.getApproval(approvalRequestId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminApprovalController.class, "getApproval(Long)");
        }
    }

    /**
     * Approves the operation.
     *
     * @param approvalRequestId the approval request id
     * @param request the request
     * @return the approve result
     */
    @PostMapping("/{approvalRequestId}/approve")
    @PreAuthorize("hasAuthority('APPROVAL_MANAGE')")
    public ResponseEntity<ApprovalRequestResponse> approve(
            @PathVariable Long approvalRequestId,
            @Valid @RequestBody ApprovalActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalController.class, "approve(Long,ApprovalActionRequest)");
        try {
            return ResponseEntity.ok(adminApprovalService.approve(approvalRequestId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalController.class,
                    "approve(Long,ApprovalActionRequest)");
        }
    }

    /**
     * Rejects the operation.
     *
     * @param approvalRequestId the approval request id
     * @param request the request
     * @return the reject result
     */
    @PostMapping("/{approvalRequestId}/reject")
    @PreAuthorize("hasAuthority('APPROVAL_MANAGE')")
    public ResponseEntity<ApprovalRequestResponse> reject(
            @PathVariable Long approvalRequestId,
            @Valid @RequestBody ApprovalActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalController.class, "reject(Long,ApprovalActionRequest)");
        try {
            return ResponseEntity.ok(adminApprovalService.reject(approvalRequestId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalController.class,
                    "reject(Long,ApprovalActionRequest)");
        }
    }

    /**
     * Sends back.
     *
     * @param approvalRequestId the approval request id
     * @param request the request
     * @return the send back result
     */
    @PostMapping("/{approvalRequestId}/send-back")
    @PreAuthorize("hasAuthority('APPROVAL_MANAGE')")
    public ResponseEntity<ApprovalRequestResponse> sendBack(
            @PathVariable Long approvalRequestId,
            @Valid @RequestBody ApprovalActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminApprovalController.class, "sendBack(Long,ApprovalActionRequest)");
        try {
            return ResponseEntity.ok(adminApprovalService.sendBack(approvalRequestId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminApprovalController.class,
                    "sendBack(Long,ApprovalActionRequest)");
        }
    }
}
