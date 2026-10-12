package com.gokulsweets.restaurant.campaign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence operations for campaign publication records. */
public interface CampaignPublicationRepository extends JpaRepository<CampaignPublication, Long> {

    /**
     * Performs the find by campaign id order by id desc operation for campaign publication
     * repository.
     *
     * @param campaignId the campaign id
     * @return the find by campaign id order by id desc result
     */
    List<CampaignPublication> findByCampaignIdOrderByIdDesc(Long campaignId);
}
