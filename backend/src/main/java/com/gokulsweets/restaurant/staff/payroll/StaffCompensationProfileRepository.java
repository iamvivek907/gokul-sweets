package com.gokulsweets.restaurant.staff.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StaffCompensationProfileRepository
        extends JpaRepository<StaffCompensationProfile, Long> {

    Optional<StaffCompensationProfile>
    findFirstByStaffUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long staffUserId,
            LocalDate effectiveDate
    );

    List<StaffCompensationProfile>
    findByStaffUserIdOrderByEffectiveFromDesc(
            Long staffUserId
    );
}
