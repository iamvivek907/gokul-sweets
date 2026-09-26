package com.gokulsweets.restaurant.campaign;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;

public interface HomepageCampaignRepository extends JpaRepository<HomepageCampaign, Long> {
    List<HomepageCampaign> findAllByOrderByDisplayOrderAscIdAsc();
    List<HomepageCampaign> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HomepageCampaign c where c.id = :id")
    Optional<HomepageCampaign> findForUpdate(@Param("id") Long id);

    @Modifying
    @Query(value = "insert into homepage_campaigns (creation_request_id,creation_request_hash,title,type) values (:key,:hash,:title,:type) on conflict (creation_request_id) do nothing", nativeQuery = true)
    int createDraft(UUID key, String hash, String title, String type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HomepageCampaign> findByCreationRequestId(UUID key);

    @Query(value = "select request_hash from campaign_media_requests where campaign_id=:id and fallback=:fallback and request_id=:key", nativeQuery = true)
    Optional<String> mediaRequestHash(Long id, boolean fallback, UUID key);

    @Modifying
    @Query(value = "insert into campaign_media_requests(campaign_id,fallback,request_id,request_hash) values (:id,:fallback,:key,:hash)", nativeQuery = true)
    void recordMediaRequest(Long id, boolean fallback, UUID key, String hash);

    @Query(value = "select request_hash from campaign_mobile_requests where campaign_id=:id and request_id=:key", nativeQuery = true)
    Optional<String> mobileRequestHash(Long id, UUID key);

    @Modifying
    @Query(value = "insert into campaign_mobile_requests(campaign_id,request_id,request_hash) values (:id,:key,:hash)", nativeQuery = true)
    void recordMobileRequest(Long id, UUID key, String hash);
}
