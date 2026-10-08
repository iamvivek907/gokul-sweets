package com.gokulsweets.restaurant.staff.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence operations for staff compensation profile records. */
public interface StaffCompensationProfileRepository
        extends JpaRepository<StaffCompensationProfile, Long> {

    /**
     * Performs the find first by staff user id and effective from less than equal order by
     * effective from desc operation for staff compensation profile repository.
     *
     * @param staffUserId the staff user id
     * @param effectiveDate the effective date
     * @return the find first by staff user id and effective from less than equal order by effective
     *     from desc result
     */
    Optional<StaffCompensationProfile>
            findFirstByStaffUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                    Long staffUserId, LocalDate effectiveDate);

    /**
     * Performs the find by staff user id order by effective from desc operation for staff
     * compensation profile repository.
     *
     * @param staffUserId the staff user id
     * @return the find by staff user id order by effective from desc result
     */
    List<StaffCompensationProfile> findByStaffUserIdOrderByEffectiveFromDesc(Long staffUserId);
}
