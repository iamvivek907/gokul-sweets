package com.gokulsweets.restaurant.staff.approval;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ApprovalRequestRepository
        extends JpaRepository<ApprovalRequest, Long>,
        JpaSpecificationExecutor<ApprovalRequest> {

    Optional<ApprovalRequest> findByRequestNumber(
            String requestNumber
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a
        FROM ApprovalRequest a
        JOIN FETCH a.staffUser
        JOIN FETCH a.branch
        WHERE a.id = :id
        """)
    Optional<ApprovalRequest> findByIdForUpdate(
            @Param("id")
            Long id
    );
}
