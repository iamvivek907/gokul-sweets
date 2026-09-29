package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.customer.identity.TrustedCheckoutIdentity;
import com.gokulsweets.restaurant.customer.identity.VerifiedCustomerSessionStore;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OccasionEnquiryController {
    private final OccasionEnquiryService service;
    private final OccasionCommitmentService commitments;
    private final EnhancementProperties features;
    private final Environment settings;
    private final TrustedCheckoutIdentity identity;
    private final VerifiedCustomerSessionStore sessions;
    private final StaffAuthorizationService staff;

    @PostMapping("/api/occasion-enquiries")
    public OccasionEnquiryService.Summary submit(@Valid @RequestBody OccasionEnquiryService.Request input,
                                                  HttpServletRequest request) {
        return service.submit(environment(), subject(request), input);
    }

    @GetMapping("/api/occasion-enquiries")
    public List<OccasionEnquiryService.Summary> mine(HttpServletRequest request) {
        return service.customerList(environment(), subject(request));
    }

    @GetMapping("/api/occasion-enquiries/{id}")
    public OccasionEnquiryService.Summary mine(@PathVariable UUID id, HttpServletRequest request) {
        return service.get(environment(), subject(request), id);
    }

    public record DepositChoice(long pickupSlotId) {}

    @PostMapping("/api/occasion-enquiries/{id}/deposit")
    public OccasionCommitmentService.Checkout deposit(@PathVariable UUID id, @RequestBody DepositChoice choice,
                                                       HttpServletRequest request) {
        return commitments.beginDeposit(environment(), subject(request), id, choice.pickupSlotId());
    }

    @PostMapping("/api/occasion-enquiries/{id}/balance")
    public OccasionCommitmentService.Checkout balance(@PathVariable UUID id, HttpServletRequest request) {
        return commitments.beginBalance(environment(), subject(request), id);
    }

    @GetMapping("/api/occasion-enquiries/{id}/payments/{attemptId}")
    public OccasionCommitmentService.Checkout payment(@PathVariable UUID id, @PathVariable UUID attemptId,
                                                       HttpServletRequest request) {
        return commitments.status(environment(), subject(request), id, attemptId);
    }

    @GetMapping("/api/occasion-enquiries/{id}/payments/latest")
    public OccasionCommitmentService.Checkout latestPayment(@PathVariable UUID id, HttpServletRequest request) {
        return commitments.latest(environment(), subject(request), id);
    }

    @GetMapping("/api/admin/branches/{branchId}/occasion-enquiries")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public List<OccasionEnquiryService.Summary> staffList(@PathVariable long branchId) {
        staff.requireBranchAccess(branchId);
        return service.staffList(environment(), branchId);
    }

    @PostMapping("/api/admin/branches/{branchId}/occasion-enquiries/{id}/quote")
    @PreAuthorize("hasAuthority('APPROVAL_MANAGE')")
    public OccasionEnquiryService.Summary quote(@PathVariable long branchId, @PathVariable UUID id,
                                                @Valid @RequestBody OccasionEnquiryService.Quote quote) {
        staff.requireBranchAccess(branchId);
        return service.quote(environment(), branchId, id, staff.getCurrentStaff().getUsername(), quote);
    }

    @PostMapping("/api/admin/branches/{branchId}/occasion-enquiries/{id}/decline")
    @PreAuthorize("hasAuthority('APPROVAL_MANAGE')")
    public OccasionEnquiryService.Summary decline(@PathVariable long branchId, @PathVariable UUID id,
                                                  @RequestBody Decline input) {
        staff.requireBranchAccess(branchId);
        return service.decline(environment(), branchId, id, staff.getCurrentStaff().getUsername(), input.reason());
    }

    public record Decline(String reason) {}

    private UUID subject(HttpServletRequest request) {
        return sessions.subject(environment(), identity.token(request), Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verify your phone first."));
    }

    private ConsentEnvironment environment() {
        if (!features.isOccasionEnquiries() || !features.isCustomerOtpIdentity()
                || !settings.getProperty("gokul.environment-isolation.enabled", Boolean.class, false)
                || !settings.getProperty("gokul.web.environment-cors-enabled", Boolean.class, false))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        try {
            return ConsentEnvironment.valueOf(settings.getProperty("gokul.environment-isolation.environment", ""));
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
