package com.gokulsweets.restaurant.campaign;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface HomepageCampaignRepository extends JpaRepository<HomepageCampaign, Long> {
    List<HomepageCampaign> findAllByOrderByDisplayOrderAscIdAsc();
    List<HomepageCampaign> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HomepageCampaign c where c.id = :id")
    Optional<HomepageCampaign> findForUpdate(@Param("id") Long id);
}
