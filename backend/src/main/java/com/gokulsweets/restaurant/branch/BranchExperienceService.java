package com.gokulsweets.restaurant.branch;

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

    public record Snapshot(long branchId, String draftImageUrl, String draftMobileUrl,
                           String draftAltText, String draftDescription, String publishedImageUrl,
                           String publishedMobileUrl, String publishedAltText, String publishedDescription,
                           long editVersion, long publishedRevision) {}
    public record Publication(long revision, String imageUrl, String mobileUrl, String altText,
                              String description, long actorStaffId) {}
    public record Copy(String altText, String description) {}

    private Snapshot row(long branchId, boolean lock) {
        jdbc.update("INSERT INTO branch_experience(branch_id) VALUES (?) ON CONFLICT DO NOTHING", branchId);
        return jdbc.queryForObject("""
                SELECT branch_id, draft_image_url, draft_mobile_url, draft_alt_text, draft_description,
                       published_image_url, published_mobile_url, published_alt_text, published_description,
                       edit_version, published_revision FROM branch_experience WHERE branch_id = ?
                """ + (lock ? " FOR UPDATE" : ""), (rs, n) -> new Snapshot(rs.getLong(1), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getString(8), rs.getString(9), rs.getLong(10), rs.getLong(11)), branchId);
    }

    private void check(long branchId) {
        authorization.requirePermission(PermissionName.BRANCH_MANAGE);
        authorization.requirePermission(PermissionName.MENU_MANAGE);
        authorization.requireBranchAccess(branchId);
        if (!branches.existsById(branchId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
    }

    private void version(Snapshot current, long expected) {
        if (current.editVersion() != expected)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Branch content changed. Reload before saving.");
    }

    private void audit(Snapshot before, Snapshot after, String action) {
        jdbc.update("""
                INSERT INTO branch_experience_audit(branch_id, actor_staff_id, action,
                    from_version, to_version, before_state, after_state) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, before.branchId(), authorization.getCurrentStaff().getId(), action,
                before.editVersion(), after.editVersion(), before.toString(), after.toString());
    }

    @Transactional
    public Snapshot get(long branchId) {check(branchId); return row(branchId, false);}

    @Transactional
    public Snapshot saveCopy(long branchId, Copy copy, long expected) {
        check(branchId);
        if (copy.altText() == null || copy.altText().isBlank() || copy.altText().length() > 180
                || copy.description() != null && copy.description().length() > 500)
            throw new IllegalArgumentException("Add image alt text (up to 180 characters) and a description up to 500 characters.");
        var before = row(branchId, true); version(before, expected);
        jdbc.update("UPDATE branch_experience SET draft_alt_text=?, draft_description=?, edit_version=edit_version+1 WHERE branch_id=?",
                copy.altText().trim(), copy.description() == null ? null : copy.description().trim(), branchId);
        var after = row(branchId, false); audit(before, after, "DRAFT"); return after;
    }

    @Transactional
    public Snapshot upload(long branchId, MultipartFile file, boolean mobile, long expected) {
        check(branchId);
        var before = row(branchId, true); version(before, expected);
        var media = storage.uploadCampaignMedia(branchId, file, true);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.deleteCampaignMedia(media.url());
            }
        });
        jdbc.update("UPDATE branch_experience SET " + (mobile ? "draft_mobile_url" : "draft_image_url")
                + "=?, edit_version=edit_version+1 WHERE branch_id=?", media.url(), branchId);
        var after = row(branchId, false); audit(before, after, mobile ? "MOBILE_UPLOAD" : "COVER_UPLOAD"); return after;
    }

    @Transactional
    public Snapshot publish(long branchId, long expected) {
        check(branchId);
        var before = row(branchId, true); version(before, expected);
        if (before.draftImageUrl() == null || before.draftAltText() == null || before.draftAltText().isBlank())
            throw new IllegalArgumentException("Upload a branch cover and add alt text before publishing.");
        long revision = before.publishedRevision() + 1;
        jdbc.update("""
                UPDATE branch_experience SET published_image_url=draft_image_url,
                    published_mobile_url=draft_mobile_url, published_alt_text=draft_alt_text,
                    published_description=draft_description, published_revision=?, edit_version=edit_version+1
                WHERE branch_id=?
                """, revision, branchId);
        jdbc.update("""
                INSERT INTO branch_experience_publications(branch_id, revision, image_url, mobile_url,
                    alt_text, description, actor_staff_id) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, branchId, revision, before.draftImageUrl(), before.draftMobileUrl(),
                before.draftAltText(), before.draftDescription(), authorization.getCurrentStaff().getId());
        var after = row(branchId, false); audit(before, after, "PUBLISH"); return after;
    }

    @Transactional
    public Snapshot restore(long branchId, long revision, long expected) {
        check(branchId);
        var before = row(branchId, true); version(before, expected);
        var old = jdbc.query("""
                SELECT revision, image_url, mobile_url, alt_text, description, actor_staff_id
                FROM branch_experience_publications WHERE branch_id=? AND revision=?
                """, (rs, n) -> new Publication(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getLong(6)), branchId, revision);
        if (old.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Publication not found.");
        var selected = old.getFirst();
        jdbc.update("""
                UPDATE branch_experience SET draft_image_url=?, draft_mobile_url=?, draft_alt_text=?,
                    draft_description=?, edit_version=edit_version+1 WHERE branch_id=?
                """, selected.imageUrl(), selected.mobileUrl(), selected.altText(), selected.description(), branchId);
        return publish(branchId, expected + 1);
    }

    @Transactional(readOnly = true)
    public List<Publication> history(long branchId) {
        check(branchId);
        return jdbc.query("""
                SELECT revision, image_url, mobile_url, alt_text, description, actor_staff_id
                FROM branch_experience_publications WHERE branch_id=? ORDER BY revision DESC
                """, (rs, n) -> new Publication(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getLong(6)), branchId);
    }

    @Transactional(readOnly = true)
    public Publication published(long branchId) {
        return jdbc.query("""
                SELECT published_revision, published_image_url, published_mobile_url,
                    published_alt_text, published_description, 0
                FROM branch_experience WHERE branch_id=? AND published_revision>0
                """, (rs, n) -> new Publication(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), 0), branchId).stream().findFirst().orElse(null);
    }
}
