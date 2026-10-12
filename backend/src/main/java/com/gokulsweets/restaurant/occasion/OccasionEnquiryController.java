package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.customer.identity.TrustedCheckoutIdentity;
import com.gokulsweets.restaurant.customer.identity.VerifiedCustomerSessionStore;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;

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

/** HTTP endpoints for occasion enquiry operations. */
@RestController
@RequiredArgsConstructor
public class OccasionEnquiryController {

    private final OccasionEnquiryService service;

    private final OccasionQuoteCalculator calculator;

    private final OccasionPackingFinalizer packing;

    private final OccasionCommitmentService commitments;

    private final OccasionProductionReadinessService readiness;

    private final OccasionProductionWorkspace workspace;

    private final OccasionCancellationService cancellations;

    private final EnhancementProperties features;

    private final Environment settings;

    private final TrustedCheckoutIdentity identity;

    private final VerifiedCustomerSessionStore sessions;

    private final StaffAuthorizationService staff;

    /**
     * Handles {@code POST /api/occasion-enquiries} for occasion enquiry.
     *
     * <p>Delegates to {@code service.submit(...)}.
     *
     * @param input the input supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code service.submit(environment(), subject(request), input)}
     */
    @PostMapping("/api/occasion-enquiries")
    public OccasionEnquiryService.Summary submit(
            @Valid @RequestBody OccasionEnquiryService.Request input, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class,
                        "submit(OccasionEnquiryService.Request,HttpServletRequest)");
        try {
            return service.submit(environment(), subject(request), input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "submit(OccasionEnquiryService.Request,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code GET /api/occasion-enquiries} for occasion enquiry.
     *
     * <p>Delegates to {@code service.customerList(...)}.
     *
     * @param request the request supplied to this method
     * @param before the before supplied to this method
     * @return the value of {@code service.customerList(environment(), subject(request), before)}
     */
    @GetMapping("/api/occasion-enquiries")
    public List<OccasionEnquiryService.Summary> mine(
            HttpServletRequest request, @RequestParam(required = false) UUID before) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class, "mine(HttpServletRequest,UUID)");
        try {
            return service.customerList(environment(), subject(request), before);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "mine(HttpServletRequest,UUID)");
        }
    }

    /**
     * Handles {@code GET /api/occasion-enquiries/{id}} for occasion enquiry.
     *
     * @param id the id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code service.get(environment(), subject(request), id)}
     */
    @GetMapping("/api/occasion-enquiries/{id}")
    public OccasionEnquiryService.Summary mine(@PathVariable UUID id, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class, "mine(UUID,HttpServletRequest)");
        try {
            return service.get(environment(), subject(request), id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "mine(UUID,HttpServletRequest)");
        }
    }

    /**
     * Immutable deposit choice data contract.
     *
     * @param pickupSlotId the pickup slot id
     * @param estimateAccepted the estimate accepted
     */
    public record DepositChoice(long pickupSlotId, boolean estimateAccepted) {}

    /**
     * Handles {@code POST /api/occasion-enquiries/{id}/deposit} for occasion enquiry.
     *
     * @param id the id supplied to this method
     * @param choice the choice supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code commitments.beginDeposit(environment(), subject(request), id,
     *     choice.pickupSlotId(), choice.estimateAccepted())}
     */
    @PostMapping("/api/occasion-enquiries/{id}/deposit")
    public OccasionCommitmentService.Checkout deposit(
            @PathVariable UUID id, @RequestBody DepositChoice choice, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class,
                        "deposit(UUID,DepositChoice,HttpServletRequest)");
        try {
            return commitments.beginDeposit(
                    environment(),
                    subject(request),
                    id,
                    choice.pickupSlotId(),
                    choice.estimateAccepted());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "deposit(UUID,DepositChoice,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code POST /api/occasion-enquiries/{id}/balance} for occasion enquiry.
     *
     * @param id the id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code commitments.beginBalance(environment(), subject(request), id)}
     */
    @PostMapping("/api/occasion-enquiries/{id}/balance")
    public OccasionCommitmentService.Checkout balance(
            @PathVariable UUID id, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class, "balance(UUID,HttpServletRequest)");
        try {
            return commitments.beginBalance(environment(), subject(request), id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "balance(UUID,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code GET /api/occasion-enquiries/{id}/payments/{attemptId}} for occasion enquiry.
     *
     * @param id the id supplied to this method
     * @param attemptId the attempt id supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code commitments.status(environment(), subject(request), id,
     *     attemptId)}
     */
    @GetMapping("/api/occasion-enquiries/{id}/payments/{attemptId}")
    public OccasionCommitmentService.Checkout payment(
            @PathVariable UUID id, @PathVariable UUID attemptId, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class, "payment(UUID,UUID,HttpServletRequest)");
        try {
            return commitments.status(environment(), subject(request), id, attemptId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "payment(UUID,UUID,HttpServletRequest)");
        }
    }

    /**
     * Latests payment.
     *
     * @param id the id
     * @param request the request
     * @return the latest payment result
     */
    @GetMapping("/api/occasion-enquiries/{id}/payments/latest")
    public OccasionCommitmentService.Checkout latestPayment(
            @PathVariable UUID id, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class, "latestPayment(UUID,HttpServletRequest)");
        try {
            return commitments.latest(environment(), subject(request), id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "latestPayment(UUID,HttpServletRequest)");
        }
    }

    /**
     * Staffs list.
     *
     * @param branchId the branch id
     * @param serviceDate the service date
     * @param before the before
     * @return the staff list result
     */
    @GetMapping("/api/admin/branches/{branchId}/occasion-enquiries")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public List<OccasionEnquiryService.Summary> staffList(
            @PathVariable long branchId,
            @RequestParam(required = false) java.time.LocalDate serviceDate,
            @RequestParam(required = false) UUID before) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class,
                        "staffList(long,java.time.LocalDate,UUID)");
        try {
            staff.requireBranchAccess(branchId);
            return serviceDate == null
                    ? service.staffList(environment(), branchId)
                    : service.staffList(environment(), branchId, serviceDate, before);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "staffList(long,java.time.LocalDate,UUID)");
        }
    }

    /**
     * Handles {@code GET /api/admin/branches/{branchId}/occasion-enquiries/planning} for occasion
     * enquiry.
     *
     * @param branchId the branch id supplied to this method
     * @param from the from supplied to this method
     * @return the value of {@code workspace.week(environment(), branchId, from)}
     */
    @GetMapping("/api/admin/branches/{branchId}/occasion-enquiries/planning")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public OccasionProductionWorkspace.Week planning(
            @PathVariable long branchId, @RequestParam(required = false) java.time.LocalDate from) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class, "planning(long,java.time.LocalDate)");
        try {
            staff.requireBranchAccess(branchId);
            return workspace.week(environment(), branchId, from);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "planning(long,java.time.LocalDate)");
        }
    }

    /**
     * Handles {@code GET /api/admin/branches/{branchId}/occasion-enquiries/calendar} for occasion
     * enquiry.
     *
     * @param branchId the branch id supplied to this method
     * @param month the month supplied to this method
     * @return the value of {@code workspace.month(environment(), branchId, month)}
     */
    @GetMapping("/api/admin/branches/{branchId}/occasion-enquiries/calendar")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public OccasionProductionWorkspace.Month calendar(
            @PathVariable long branchId,
            @RequestParam(required = false)
                    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM")
                    java.time.YearMonth month) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class, "calendar(long,java.time.YearMonth)");
        try {
            staff.requireBranchAccess(branchId);
            return workspace.month(environment(), branchId, month);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "calendar(long,java.time.YearMonth)");
        }
    }

    /**
     * Staffs request.
     *
     * @param branchId the branch id
     * @param id the id
     * @return the staff request result
     */
    @GetMapping("/api/admin/branches/{branchId}/occasion-enquiries/{id}")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public OccasionEnquiryService.Summary staffRequest(
            @PathVariable long branchId, @PathVariable UUID id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionEnquiryController.class, "staffRequest(long,UUID)");
        try {
            staff.requireBranchAccess(branchId);
            return service.staffGet(environment(), branchId, id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "staffRequest(long,UUID)");
        }
    }

    /**
     * Approves production.
     *
     * @param branchId the branch id
     * @param date the date
     * @param productId the product id
     * @param input the input
     * @return the approve production result
     */
    @PostMapping(
            "/api/admin/branches/{branchId}/occasion-enquiries/production/{date}/{productId}/approve")
    @PreAuthorize("hasAuthority('APPROVAL_MANAGE')")
    public java.util.Map<String, Integer> approveProduction(
            @PathVariable long branchId,
            @PathVariable java.time.LocalDate date,
            @PathVariable long productId,
            @RequestBody OccasionProductionWorkspace.Approval input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class,
                        "approveProduction(long,java.time.LocalDate,long,OccasionProductionWorkspace.Approval)");
        try {
            staff.requireBranchAccess(branchId);
            return java.util.Map.of(
                    "updatedCount",
                    workspace.approve(
                            environment(),
                            branchId,
                            date,
                            productId,
                            input.token(),
                            staff.getCurrentStaff().getUsername()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "approveProduction(long,java.time.LocalDate,long,OccasionProductionWorkspace.Approval)");
        }
    }

    /**
     * Handles {@code POST /api/admin/branches/{branchId}/occasion-enquiries/{id}/quote} for
     * occasion enquiry.
     *
     * <p>Delegates to {@code service.quote(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param id the id supplied to this method
     * @param quote the quote supplied to this method
     * @return the value of {@code service.quote(environment(), branchId, id,
     *     staff.getCurrentStaff().getUsername(), quote)}
     */
    @PostMapping("/api/admin/branches/{branchId}/occasion-enquiries/{id}/quote")
    @PreAuthorize("hasAuthority('APPROVAL_MANAGE')")
    public OccasionEnquiryService.Summary quote(
            @PathVariable long branchId,
            @PathVariable UUID id,
            @Valid @RequestBody OccasionEnquiryService.Quote quote) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class,
                        "quote(long,UUID,OccasionEnquiryService.Quote)");
        try {
            staff.requireBranchAccess(branchId);
            return service.quote(
                    environment(), branchId, id, staff.getCurrentStaff().getUsername(), quote);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "quote(long,UUID,OccasionEnquiryService.Quote)");
        }
    }

    /**
     * Handles {@code POST /api/admin/branches/{branchId}/occasion-enquiries/{id}/quote-preview} for
     * occasion enquiry.
     *
     * @param branchId the branch id supplied to this method
     * @param id the id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code calculator.preview(environment(), branchId, id, input)}
     */
    @PostMapping("/api/admin/branches/{branchId}/occasion-enquiries/{id}/quote-preview")
    @PreAuthorize("hasAuthority('APPROVAL_MANAGE')")
    public OccasionQuoteCalculator.Calculation preview(
            @PathVariable long branchId,
            @PathVariable UUID id,
            @Valid @RequestBody OccasionQuoteCalculator.Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class,
                        "preview(long,UUID,OccasionQuoteCalculator.Input)");
        try {
            staff.requireBranchAccess(branchId);
            return calculator.preview(environment(), branchId, id, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "preview(long,UUID,OccasionQuoteCalculator.Input)");
        }
    }

    /**
     * Approves rates.
     *
     * @param branchId the branch id
     * @param id the id
     * @param input the input
     * @return the approve rates result
     */
    @PostMapping("/api/admin/branches/{branchId}/occasion-enquiries/{id}/quote-from-rates")
    @PreAuthorize("hasAuthority('APPROVAL_MANAGE')")
    public OccasionEnquiryService.Summary approveRates(
            @PathVariable long branchId,
            @PathVariable UUID id,
            @Valid @RequestBody OccasionQuoteCalculator.Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class,
                        "approveRates(long,UUID,OccasionQuoteCalculator.Input)");
        try {
            staff.requireBranchAccess(branchId);
            return calculator.approve(
                    environment(), branchId, id, staff.getCurrentStaff().getUsername(), input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "approveRates(long,UUID,OccasionQuoteCalculator.Input)");
        }
    }

    /**
     * Finalizes packing.
     *
     * @param branchId the branch id
     * @param id the id
     * @param input the input
     * @return the finalize packing result
     */
    @PostMapping("/api/admin/branches/{branchId}/occasion-enquiries/{id}/finalize-packing")
    @PreAuthorize("hasAuthority('APPROVAL_MANAGE')")
    public OccasionEnquiryService.Summary finalizePacking(
            @PathVariable long branchId,
            @PathVariable UUID id,
            @Valid @RequestBody OccasionPackingFinalizer.Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class,
                        "finalizePacking(long,UUID,OccasionPackingFinalizer.Input)");
        try {
            staff.requireBranchAccess(branchId);
            return packing.finalizePacking(
                    environment(), branchId, id, staff.getCurrentStaff().getUsername(), input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "finalizePacking(long,UUID,OccasionPackingFinalizer.Input)");
        }
    }

    /**
     * Handles {@code POST /api/admin/branches/{branchId}/occasion-enquiries/{id}/decline} for
     * occasion enquiry.
     *
     * <p>Delegates to {@code service.decline(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param id the id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code service.decline(environment(), branchId, id,
     *     staff.getCurrentStaff().getUsername(), input.reason())}
     */
    @PostMapping("/api/admin/branches/{branchId}/occasion-enquiries/{id}/decline")
    @PreAuthorize("hasAuthority('APPROVAL_MANAGE')")
    public OccasionEnquiryService.Summary decline(
            @PathVariable long branchId, @PathVariable UUID id, @RequestBody Decline input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionEnquiryController.class, "decline(long,UUID,Decline)");
        try {
            staff.requireBranchAccess(branchId);
            return service.decline(
                    environment(),
                    branchId,
                    id,
                    staff.getCurrentStaff().getUsername(),
                    input.reason());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "decline(long,UUID,Decline)");
        }
    }

    /**
     * Immutable ready quantity data contract.
     *
     * @param quantity the quantity
     * @param revision the revision
     */
    public record ReadyQuantity(java.math.BigDecimal quantity, long revision) {}

    /**
     * Handles {@code POST
     * /api/admin/branches/{branchId}/occasion-enquiries/{id}/production/{productId}/readiness} for
     * occasion enquiry.
     *
     * <p>Delegates to {@code service.staffGet(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param id the id supplied to this method
     * @param productId the product id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code service.staffGet(environment(), branchId, id)}
     */
    @PostMapping(
            "/api/admin/branches/{branchId}/occasion-enquiries/{id}/production/{productId}/readiness")
    @PreAuthorize("hasAuthority('ORDER_MARK_READY')")
    public OccasionEnquiryService.Summary readiness(
            @PathVariable long branchId,
            @PathVariable UUID id,
            @PathVariable long productId,
            @RequestBody ReadyQuantity input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionEnquiryController.class, "readiness(long,UUID,long,ReadyQuantity)");
        try {
            staff.requireBranchAccess(branchId);
            readiness.record(
                    environment(),
                    branchId,
                    id,
                    productId,
                    input.quantity(),
                    input.revision(),
                    staff.getCurrentStaff().getUsername());
            return service.staffGet(environment(), branchId, id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "readiness(long,UUID,long,ReadyQuantity)");
        }
    }

    /**
     * Handles {@code POST /api/admin/branches/{branchId}/occasion-enquiries/{id}/cancel} for
     * occasion enquiry.
     *
     * <p>Delegates to {@code service.staffGet(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param id the id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code service.staffGet(environment(), branchId, id)}
     */
    @PostMapping("/api/admin/branches/{branchId}/occasion-enquiries/{id}/cancel")
    @PreAuthorize("hasAuthority('APPROVAL_MANAGE')")
    public OccasionEnquiryService.Summary cancel(
            @PathVariable long branchId, @PathVariable UUID id, @RequestBody Decline input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionEnquiryController.class, "cancel(long,UUID,Decline)");
        try {
            staff.requireBranchAccess(branchId);
            cancellations.cancel(
                    environment(),
                    branchId,
                    id,
                    staff.getCurrentStaff().getUsername(),
                    input.reason());
            return service.staffGet(environment(), branchId, id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "cancel(long,UUID,Decline)");
        }
    }

    /**
     * Immutable decline data contract.
     *
     * @param reason the reason
     */
    public record Decline(String reason) {}

    /**
     * Returns subject information for occasion enquiry.
     *
     * @param request the request supplied to this method
     * @return the {@code UUID} result
     */
    private UUID subject(HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionEnquiryController.class, "subject(HttpServletRequest)");
        try {
            return sessions.subject(environment(), identity.token(request), Instant.now())
                    .orElseThrow(
                            () ->
                                    new ResponseStatusException(
                                            HttpStatus.UNAUTHORIZED, "Verify your phone first."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionEnquiryController.class,
                    "subject(HttpServletRequest)");
        }
    }

    /**
     * Returns environment information for occasion enquiry.
     *
     * @return the {@code ConsentEnvironment} result
     */
    private ConsentEnvironment environment() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionEnquiryController.class, "environment()");
        try {
            if (!features.isOccasionEnquiries()
                    || !features.isCustomerOtpIdentity()
                    || !settings.getProperty(
                            "gokul.environment-isolation.enabled", Boolean.class, false)
                    || !settings.getProperty(
                            "gokul.web.environment-cors-enabled", Boolean.class, false))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            try {
                return ConsentEnvironment.valueOf(
                        settings.getProperty("gokul.environment-isolation.environment", ""));
            } catch (IllegalArgumentException invalid) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionEnquiryController.class, "environment()");
        }
    }
}
