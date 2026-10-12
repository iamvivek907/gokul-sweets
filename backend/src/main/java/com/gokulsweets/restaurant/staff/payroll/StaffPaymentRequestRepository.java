package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

/** Persistence operations for staff payment request records. */
public interface StaffPaymentRequestRepository extends JpaRepository<StaffPaymentRequest, Long> {

    /**
     * Finds by staff user id.
     *
     * @param staffUserId the staff user id
     * @param pageable the pageable
     * @return the find by staff user id result
     */
    @EntityGraph(attributePaths = {"branch", "approvalRequest"})
    Page<StaffPaymentRequest> findByStaffUserId(Long staffUserId, Pageable pageable);

    /**
     * Finds own detailed by id.
     *
     * @param paymentRequestId the payment request id
     * @param staffUserId the staff user id
     * @return the find own detailed by id result
     */
    @EntityGraph(attributePaths = {"staffUser", "branch", "approvalRequest"})
    @Query(
            """
            SELECT p
            FROM StaffPaymentRequest p
            WHERE p.id = :paymentRequestId
              AND p.staffUser.id = :staffUserId
            """)
    Optional<StaffPaymentRequest> findOwnDetailedById(
            @Param("paymentRequestId") Long paymentRequestId,
            @Param("staffUserId") Long staffUserId);

    /**
     * Sums committed amount.
     *
     * @param staffUserId the staff user id
     * @param statuses the statuses
     * @param excludePaymentRequestId the exclude payment request id
     * @return the sum committed amount result
     */
    @Query(
            """
            SELECT COALESCE(SUM(p.amount), 0)
            FROM StaffPaymentRequest p
            WHERE p.staffUser.id = :staffUserId
              AND p.approvalRequest.status IN :statuses
              AND (:excludePaymentRequestId IS NULL OR p.id <> :excludePaymentRequestId)
            """)
    BigDecimal sumCommittedAmount(
            @Param("staffUserId") Long staffUserId,
            @Param("statuses") Collection<ApprovalRequestStatus> statuses,
            @Param("excludePaymentRequestId") Long excludePaymentRequestId);
}
