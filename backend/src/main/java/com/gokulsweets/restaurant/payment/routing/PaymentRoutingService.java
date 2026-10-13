package com.gokulsweets.restaurant.payment.routing;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.provider.PaymentProvider;
import com.gokulsweets.restaurant.payment.provider.PaymentProviderRegistry;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Selects new attempts independently from processing historical provider obligations. */
@Service
@RequiredArgsConstructor
public class PaymentRoutingService {
    private final PaymentRoutingStore store;
    private final PaymentProviderRegistry registry;
    private final PaymentProviderReadiness readiness;
    private final StaffAuthorizationService staff;

    /**
     * Owner workspace without any keys or tokens.
     *
     * @param activeProvider the route for new attempts
     * @param revision the concurrency revision
     * @param providers supported configuration statuses
     * @param history recent switches
     */
    public record Workspace(
            PaymentProviderType activeProvider,
            long revision,
            List<PaymentProviderReadiness.Status> providers,
            List<PaymentRoutingStore.Change> history) {}

    /**
     * Resolves a selection's deployment bootstrap default.
     *
     * @param selection the stored selection
     * @return the active provider
     */
    private PaymentProviderType active(PaymentRoutingStore.Selection selection) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentRoutingService.class, "active(PaymentRoutingStore.Selection)");
        try {

            return selection.provider() == null ? registry.defaultProvider() : selection.provider();

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentRoutingService.class,
                    "active(PaymentRoutingStore.Selection)");
        }
    }

    /**
     * Reads the committed route for customer discovery.
     *
     * @return the active provider
     */
    public PaymentProviderType activeProvider() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRoutingService.class, "activeProvider()");
        try {
            return active(store.read());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentRoutingService.class, "activeProvider()");
        }
    }

    /**
     * Chooses the current route, ignoring stale browser provider preferences.
     *
     * @return the ready provider for a new payment attempt
     */
    public PaymentProvider forNewPayment() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRoutingService.class, "forNewPayment()");
        try {

            PaymentProviderType selected = activeProvider();
            var status = readiness.check(selected);
            if (!status.ready())
                throw new IllegalStateException(
                        "The active payment provider is not ready. Please contact the shop or try"
                            + " again later.");
            return registry.registered(selected);

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentRoutingService.class, "forNewPayment()");
        }
    }

    /**
     * Requires a currently active owner, including after role changes.
     *
     * @return the owner
     */
    private StaffUser owner() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRoutingService.class, "owner()");
        try {

            var user = staff.getCurrentStaff();
            if (!user.isActive() || !"OWNER_ADMIN".equals(user.getRole().getName()))
                throw new AccessDeniedException(
                        "Only an active owner may manage payment providers.");
            return user;

        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, PaymentRoutingService.class, "owner()");
        }
    }

    /**
     * Returns a bounded, secret-free owner workspace.
     *
     * @return the workspace
     */
    @Transactional(readOnly = true)
    public Workspace workspace() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRoutingService.class, "workspace()");
        try {

            owner();
            return workspace(store.read());

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentRoutingService.class, "workspace()");
        }
    }

    /**
     * Builds a workspace from one routing snapshot.
     *
     * @param selection the snapshot
     * @return the workspace
     */
    private Workspace workspace(PaymentRoutingStore.Selection selection) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentRoutingService.class, "workspace(PaymentRoutingStore.Selection)");
        try {

            return new Workspace(
                    active(selection),
                    selection.revision(),
                    List.of(
                            readiness.check(PaymentProviderType.PHONEPE),
                            readiness.check(PaymentProviderType.RAZORPAY)),
                    store.history());

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentRoutingService.class,
                    "workspace(PaymentRoutingStore.Selection)");
        }
    }

    /**
     * Atomically changes the route with optimistic concurrency and an audit reason.
     *
     * @param selected the selected supported provider
     * @param expectedRevision the owner's last observed revision
     * @param reason the audit reason
     * @return the updated workspace
     */
    @Transactional
    public Workspace activate(PaymentProviderType selected, long expectedRevision, String reason) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentRoutingService.class, "activate(PaymentProviderType,long,String)");
        try {

            var actor = owner();
            if (selected != PaymentProviderType.PHONEPE && selected != PaymentProviderType.RAZORPAY)
                throw new IllegalArgumentException("Select PhonePe or Razorpay.");
            if (reason == null || reason.isBlank() || reason.trim().length() > 300)
                throw new IllegalArgumentException(
                        "Provide a switch reason of 1 to 300 characters.");
            var selection = store.lock();
            if (selection.revision() != expectedRevision)
                throw new IllegalStateException(
                        "Payment settings changed. Refresh and review the active provider before"
                            + " switching.");
            if (!readiness.check(selected).ready())
                throw new IllegalArgumentException(
                        "This provider is not configured for this environment. Resolve its"
                            + " readiness issues first.");
            if (active(selection) != selected) {
                store.save(
                        active(selection),
                        selected,
                        selection.revision() + 1,
                        actor,
                        reason.trim());
                selection = new PaymentRoutingStore.Selection(selected, selection.revision() + 1);
            }
            return workspace(selection);

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentRoutingService.class,
                    "activate(PaymentProviderType,long,String)");
        }
    }
}
