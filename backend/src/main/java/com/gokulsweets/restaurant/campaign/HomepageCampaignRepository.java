package com.gokulsweets.restaurant.campaign;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence operations for homepage campaign records. */
public interface HomepageCampaignRepository extends JpaRepository<HomepageCampaign, Long> {

    /**
     * Performs the find all by order by display order asc id asc operation for homepage campaign
     * repository.
     *
     * @return the find all by order by display order asc id asc result
     */
    List<HomepageCampaign> findAllByOrderByDisplayOrderAscIdAsc();

    /**
     * Performs the find by active true order by display order asc id asc operation for homepage
     * campaign repository.
     *
     * @return the find by active true order by display order asc id asc result
     */
    List<HomepageCampaign> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    /**
     * Finds for update.
     *
     * @param id the id
     * @return the find for update result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HomepageCampaign c where c.id = :id")
    Optional<HomepageCampaign> findForUpdate(@Param("id") Long id);

    /**
     * Creates draft.
     *
     * @param key the key
     * @param hash the hash
     * @param title the title
     * @param type the type
     * @return the create draft result
     */
    @Modifying
    @Query(
            value =
                    "insert into homepage_campaigns"
                        + " (creation_request_id,creation_request_hash,title,type) values"
                        + " (:key,:hash,:title,:type) on conflict (creation_request_id) do nothing",
            nativeQuery = true)
    int createDraft(UUID key, String hash, String title, String type);

    /**
     * Finds by creation request id.
     *
     * @param key the key
     * @return the find by creation request id result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HomepageCampaign> findByCreationRequestId(UUID key);

    /**
     * Medias request hash.
     *
     * @param id the id
     * @param fallback the fallback
     * @param key the key
     * @return the media request hash result
     */
    @Query(
            value =
                    "select request_hash from campaign_media_requests where campaign_id=:id and"
                            + " fallback=:fallback and request_id=:key",
            nativeQuery = true)
    Optional<String> mediaRequestHash(Long id, boolean fallback, UUID key);

    /**
     * Records media request.
     *
     * @param id the id
     * @param fallback the fallback
     * @param key the key
     * @param hash the hash
     */
    @Modifying
    @Query(
            value =
                    "insert into"
                        + " campaign_media_requests(campaign_id,fallback,request_id,request_hash)"
                        + " values (:id,:fallback,:key,:hash)",
            nativeQuery = true)
    void recordMediaRequest(Long id, boolean fallback, UUID key, String hash);

    /**
     * Mobiles request hash.
     *
     * @param id the id
     * @param key the key
     * @return the mobile request hash result
     */
    @Query(
            value =
                    "select request_hash from campaign_mobile_requests where campaign_id=:id and"
                            + " request_id=:key",
            nativeQuery = true)
    Optional<String> mobileRequestHash(Long id, UUID key);

    /**
     * Records mobile request.
     *
     * @param id the id
     * @param key the key
     * @param hash the hash
     */
    @Modifying
    @Query(
            value =
                    "insert into campaign_mobile_requests(campaign_id,request_id,request_hash)"
                            + " values (:id,:key,:hash)",
            nativeQuery = true)
    void recordMobileRequest(Long id, UUID key, String hash);
}
