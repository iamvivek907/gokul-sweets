package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.storage.R2StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class BranchExperienceIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BranchExperienceService experience;
    @Autowired BranchService branches;
    @Autowired EnhancementProperties flags;
    @MockitoBean StaffAuthorizationService authorization;
    @MockitoBean R2StorageService storage;

    @Test
    void draftIsPrivateUntilPublishAndStaleEditsCannotReplaceIt() {
        var key = UUID.randomUUID().toString().substring(0, 8);
        Long id = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, ?) RETURNING id",
                Long.class, "EXP-" + key, "Branch " + key);
        var staff = new StaffUser(); staff.setId(7L);
        when(authorization.getCurrentStaff()).thenReturn(staff);
        when(storage.uploadCampaignMedia(eq(id), any(), eq(true)))
                .thenReturn(new R2StorageService.CampaignMedia("https://media.example/branch.jpg", "image/jpeg"));
        var draft = experience.saveCopy(id, new BranchExperienceService.Copy("Branch interior", "Visit us"), 0);
        var media = experience.upload(id, new MockMultipartFile("file", "branch.jpg", "image/jpeg", new byte[]{1}), false, draft.editVersion());
        assertThatThrownBy(() -> experience.saveCopy(id, new BranchExperienceService.Copy("Old editor", "Wrong"), 0))
                .isInstanceOf(ResponseStatusException.class);
        boolean wasEnabled = flags.isBranchExperience();
        try {
            flags.setBranchExperience(true);
            assertThat(branches.getBranch(id).coverImageUrl()).isNull();
            var live = experience.publish(id, media.editVersion());
            assertThat(live.publishedRevision()).isEqualTo(1);
            assertThat(branches.getBranch(id).coverImageUrl()).isEqualTo("https://media.example/branch.jpg");
            assertThat(experience.history(id)).hasSize(1);
            flags.setBranchExperience(false);
            assertThat(branches.getBranch(id).coverImageUrl()).isNull();
        } finally {flags.setBranchExperience(wasEnabled);}
    }

    @Test
    void branchScopeIsRequiredForContentMutations() {
        var key = UUID.randomUUID().toString().substring(0, 8);
        Long id = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, ?) RETURNING id",
                Long.class, "EXP-" + key, "Branch " + key);
        doThrow(new AccessDeniedException("wrong branch")).when(authorization).requireBranchAccess(id);
        assertThatThrownBy(() -> experience.saveCopy(id,
                new BranchExperienceService.Copy("Interior", "Description"), 0))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_experience WHERE branch_id=?", Integer.class, id))
                .isZero();
    }
}
