package com.gokulsweets.restaurant.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampaignPublicationRepository extends JpaRepository<CampaignPublication, Long> {
    List<CampaignPublication> findByCampaignIdOrderByIdDesc(Long campaignId);
}
