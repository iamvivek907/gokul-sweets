package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.storage.R2StorageService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Draft and publication state for branch-specific artwork; public reads never use drafts. */
@Service
@RequiredArgsConstructor
public class BranchExperienceService {

    private final JdbcTemplate jdbc;

    private final StaffAuthorizationService authorization;

    private final R2StorageService storage;

    private final BranchRepository branches;

    /**
     * Immutable snapshot data contract.
     *
     * @param branchId the branch id
     * @param draftImageUrl the draft image url
     * @param draftMobileUrl the draft mobile url
     * @param draftAltText the draft alt text
     * @param draftDescription the draft description
     * @param publishedImageUrl the published image url
     * @param publishedMobileUrl the published mobile url
     * @param publishedAltText the published alt text
     * @param publishedDescription the published description
     * @param editVersion the edit version
     * @param publishedRevision the published revision
     */
    public record Snapshot(
            long branchId,
            String draftImageUrl,
            String draftMobileUrl,
            String draftAltText,
            String draftDescription,
            String publishedImageUrl,
            String publishedMobileUrl,
            String publishedAltText,
            String publishedDescription,
            long editVersion,
            long publishedRevision) {}

    /**
     * Immutable publication data contract.
     *
     * @param revision the revision
     * @param imageUrl the image url
     * @param mobileUrl the mobile url
     * @param altText the alt text
     * @param description the description
     * @param actorStaffId the actor staff id
     */
    public record Publication(
            long revision,
            String imageUrl,
            String mobileUrl,
            String altText,
            String description,
            long actorStaffId) {}

    /**
     * Immutable copy data contract.
     *
     * @param altText the alt text
     * @param description the description
     */
    public record Copy(String altText, String description) {}

    /**
     * Returns row information for branch experience.
     *
     * <p>Writes {@code branch_experience}.
     *
     * @param branchId the branch id supplied to this method
     * @param lock the lock supplied to this method
     * @return the {@code Snapshot} result
     */
    private Snapshot row(long branchId, boolean lock) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceService.class, "row(long,boolean)");
        try {
            jdbc.update(
                    "INSERT INTO branch_experience(branch_id) VALUES (?) ON CONFLICT DO NOTHING",
                    branchId);
            return jdbc.queryForObject(
                    """
SELECT branch_id, draft_image_url, draft_mobile_url, draft_alt_text, draft_description,
       published_image_url, published_mobile_url, published_alt_text, published_description,
       edit_version, published_revision FROM branch_experience WHERE branch_id = ?
"""
                            + (lock ? " FOR UPDATE" : ""),
                    (rs, n) ->
                            new Snapshot(
                                    rs.getLong(1),
                                    rs.getString(2),
                                    rs.getString(3),
                                    rs.getString(4),
                                    rs.getString(5),
                                    rs.getString(6),
                                    rs.getString(7),
                                    rs.getString(8),
                                    rs.getString(9),
                                    rs.getLong(10),
                                    rs.getLong(11)),
                    branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchExperienceService.class, "row(long,boolean)");
        }
    }

    /**
     * Returns check information for branch experience.
     *
     * <p>Authorization checks include {@code PermissionName.BRANCH_MANAGE}, {@code
     * PermissionName.MENU_MANAGE}.
     *
     * @param branchId the branch id supplied to this method
     * @throws ResponseStatusException when the method rejects the request with {@code Branch not
     *     found.}
     */
    private void check(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceService.class, "check(long)");
        try {
            authorization.requirePermission(PermissionName.BRANCH_MANAGE);
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            authorization.requireBranchAccess(branchId);
            if (!branches.existsById(branchId))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchExperienceService.class, "check(long)");
        }
    }

    /**
     * Returns version information for branch experience.
     *
     * @param current the current supplied to this method
     * @param expected the expected supplied to this method
     * @throws ResponseStatusException when the method rejects the request with {@code Branch
     *     content changed. Reload before saving.}
     */
    private void version(Snapshot current, long expected) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceService.class, "version(Snapshot,long)");
        try {
            if (current.editVersion() != expected)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Branch content changed. Reload before saving.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchExperienceService.class,
                    "version(Snapshot,long)");
        }
    }

    /**
     * Records an audit entry for branch experience data.
     *
     * <p>Writes {@code branch_experience_audit}.
     *
     * @param before the before supplied to this method
     * @param after the after supplied to this method
     * @param action the action supplied to this method
     */
    private void audit(Snapshot before, Snapshot after, String action) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BranchExperienceService.class, "audit(Snapshot,Snapshot,String)");
        try {
            jdbc.update(
                    """
INSERT INTO branch_experience_audit(branch_id, actor_staff_id, action,
    from_version, to_version, before_state, after_state) VALUES (?, ?, ?, ?, ?, ?, ?)
""",
                    before.branchId(),
                    authorization.getCurrentStaff().getId(),
                    action,
                    before.editVersion(),
                    after.editVersion(),
                    before.toString(),
                    after.toString());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchExperienceService.class,
                    "audit(Snapshot,Snapshot,String)");
        }
    }

    /**
     * Returns get information for branch experience.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code row(branchId, false)}
     */
    @Transactional
    public Snapshot get(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceService.class, "get(long)");
        try {
            check(branchId);
            return row(branchId, false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchExperienceService.class, "get(long)");
        }
    }

    /**
     * Saves copy.
     *
     * @param branchId the branch id
     * @param copy the copy
     * @param expected the expected
     * @return the save copy result
     */
    @Transactional
    public Snapshot saveCopy(long branchId, Copy copy, long expected) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceService.class, "saveCopy(long,Copy,long)");
        try {
            check(branchId);
            if (copy.altText() == null
                    || copy.altText().isBlank()
                    || copy.altText().length() > 180
                    || copy.description() != null && copy.description().length() > 500)
                throw new IllegalArgumentException(
                        "Add image alt text (up to 180 characters) and a description up to 500"
                                + " characters.");
            var before = row(branchId, true);
            version(before, expected);
            jdbc.update(
                    "UPDATE branch_experience SET draft_alt_text=?, draft_description=?,"
                            + " edit_version=edit_version+1 WHERE branch_id=?",
                    copy.altText().trim(),
                    copy.description() == null ? null : copy.description().trim(),
                    branchId);
            var after = row(branchId, false);
            audit(before, after, "DRAFT");
            return after;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchExperienceService.class,
                    "saveCopy(long,Copy,long)");
        }
    }

    /**
     * Uploads branch experience data and returns the {@code Snapshot} result.
     *
     * <p>Delegates to {@code storage.uploadCampaignMedia(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param file the file supplied to this method
     * @param mobile the mobile supplied to this method
     * @param expected the expected supplied to this method
     * @return the value of {@code after}
     */
    @Transactional
    public Snapshot upload(long branchId, MultipartFile file, boolean mobile, long expected) {
        final long __gokulMethodStartedNanos_ =
                MethodTiming.start(
                        BranchExperienceService.class, "upload(long,MultipartFile,boolean,long)");
        try {
            check(branchId);
            var before = row(branchId, true);
            version(before, expected);
            var media = storage.uploadCampaignMedia(branchId, file, true);
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {

                        /**
                         * Afters completion.
                         *
                         * @param status the status
                         */
                        @Override
                        public void afterCompletion(int status) {
                            final long __gokulMethodStartedNanos =
                                    MethodTiming.start(
                                            BranchExperienceService.class, "afterCompletion(int)");
                            try {
                                if (status != STATUS_COMMITTED)
                                    storage.deleteCampaignMedia(media.url());
                            } finally {
                                MethodTiming.finish(
                                        __gokulMethodStartedNanos,
                                        BranchExperienceService.class,
                                        "afterCompletion(int)");
                            }
                        }
                    });
            jdbc.update(
                    "UPDATE branch_experience SET "
                            + (mobile ? "draft_mobile_url" : "draft_image_url")
                            + "=?, edit_version=edit_version+1 WHERE branch_id=?",
                    media.url(),
                    branchId);
            var after = row(branchId, false);
            audit(before, after, mobile ? "MOBILE_UPLOAD" : "COVER_UPLOAD");
            return after;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos_,
                    BranchExperienceService.class,
                    "upload(long,MultipartFile,boolean,long)");
        }
    }

    /**
     * Publishes branch experience data and returns the {@code Snapshot} result.
     *
     * <p>Writes {@code branch_experience}, {@code branch_experience_publications}.
     *
     * @param branchId the branch id supplied to this method
     * @param expected the expected supplied to this method
     * @return the value of {@code after}
     * @throws IllegalArgumentException when the method rejects the request with {@code Upload a
     *     branch cover and add alt text before publishing.}
     */
    @Transactional
    public Snapshot publish(long branchId, long expected) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceService.class, "publish(long,long)");
        try {
            check(branchId);
            var before = row(branchId, true);
            version(before, expected);
            if (before.draftImageUrl() == null
                    || before.draftAltText() == null
                    || before.draftAltText().isBlank())
                throw new IllegalArgumentException(
                        "Upload a branch cover and add alt text before publishing.");
            long revision = before.publishedRevision() + 1;
            jdbc.update(
                    """
UPDATE branch_experience SET published_image_url=draft_image_url,
    published_mobile_url=draft_mobile_url, published_alt_text=draft_alt_text,
    published_description=draft_description, published_revision=?, edit_version=edit_version+1
WHERE branch_id=?
""",
                    revision,
                    branchId);
            jdbc.update(
                    """
INSERT INTO branch_experience_publications(branch_id, revision, image_url, mobile_url,
    alt_text, description, actor_staff_id) VALUES (?, ?, ?, ?, ?, ?, ?)
""",
                    branchId,
                    revision,
                    before.draftImageUrl(),
                    before.draftMobileUrl(),
                    before.draftAltText(),
                    before.draftDescription(),
                    authorization.getCurrentStaff().getId());
            var after = row(branchId, false);
            audit(before, after, "PUBLISH");
            return after;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchExperienceService.class, "publish(long,long)");
        }
    }

    /**
     * Restores branch experience data and returns the {@code Snapshot} result.
     *
     * <p>Reads {@code branch_experience_publications}.
     *
     * <p>Writes {@code branch_experience}.
     *
     * @param branchId the branch id supplied to this method
     * @param revision the revision supplied to this method
     * @param expected the expected supplied to this method
     * @return the value of {@code publish(branchId, expected + 1)}
     * @throws ResponseStatusException when the method rejects the request with {@code Publication
     *     not found.}
     */
    @Transactional
    public Snapshot restore(long branchId, long revision, long expected) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceService.class, "restore(long,long,long)");
        try {
            check(branchId);
            var before = row(branchId, true);
            version(before, expected);
            var old =
                    jdbc.query(
                            """
SELECT revision, image_url, mobile_url, alt_text, description, actor_staff_id
FROM branch_experience_publications WHERE branch_id=? AND revision=?
""",
                            (rs, n) ->
                                    new Publication(
                                            rs.getLong(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getString(4),
                                            rs.getString(5),
                                            rs.getLong(6)),
                            branchId,
                            revision);
            if (old.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Publication not found.");
            var selected = old.getFirst();
            jdbc.update(
                    """
UPDATE branch_experience SET draft_image_url=?, draft_mobile_url=?, draft_alt_text=?,
    draft_description=?, edit_version=edit_version+1 WHERE branch_id=?
""",
                    selected.imageUrl(),
                    selected.mobileUrl(),
                    selected.altText(),
                    selected.description(),
                    branchId);
            return publish(branchId, expected + 1);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchExperienceService.class,
                    "restore(long,long,long)");
        }
    }

    /**
     * Returns history information for branch experience.
     *
     * <p>Reads {@code branch_experience_publications}.
     *
     * @param branchId the branch id supplied to this method
     * @return the {@code List<Publication>} result
     */
    @Transactional(readOnly = true)
    public List<Publication> history(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceService.class, "history(long)");
        try {
            check(branchId);
            return jdbc.query(
                    """
                    SELECT revision, image_url, mobile_url, alt_text, description, actor_staff_id
                    FROM branch_experience_publications WHERE branch_id=? ORDER BY revision DESC
                    """,
                    (rs, n) ->
                            new Publication(
                                    rs.getLong(1),
                                    rs.getString(2),
                                    rs.getString(3),
                                    rs.getString(4),
                                    rs.getString(5),
                                    rs.getLong(6)),
                    branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchExperienceService.class, "history(long)");
        }
    }

    /**
     * Returns published information for branch experience.
     *
     * <p>Reads {@code branch_experience}.
     *
     * @param branchId the branch id supplied to this method
     * @return the {@code Publication} result
     */
    @Transactional(readOnly = true)
    public Publication published(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchExperienceService.class, "published(long)");
        try {
            return jdbc
                    .query(
                            """
                            SELECT published_revision, published_image_url, published_mobile_url,
                                published_alt_text, published_description, 0
                            FROM branch_experience WHERE branch_id=? AND published_revision>0
                            """,
                            (rs, n) ->
                                    new Publication(
                                            rs.getLong(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getString(4),
                                            rs.getString(5),
                                            0),
                            branchId)
                    .stream()
                    .findFirst()
                    .orElse(null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchExperienceService.class, "published(long)");
        }
    }
}
