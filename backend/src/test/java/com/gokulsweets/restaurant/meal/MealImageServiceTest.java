package com.gokulsweets.restaurant.meal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.storage.R2StorageService;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class MealImageServiceTest {
    private final StaffAuthorizationService authorization = mock(StaffAuthorizationService.class);
    private final R2StorageService storage = mock(R2StorageService.class);
    private final MealImageService service = new MealImageService(authorization, storage);
    private final MockMultipartFile photo =
            new MockMultipartFile("image", "meal.png", "image/png", new byte[] {1});

    @Test
    void uploadsStaticArtworkOnlyAfterMenuAndBranchAuthorization() {
        var media =
                new R2StorageService.CampaignMedia("https://images.example/meal.png", "image/png");
        when(storage.uploadCampaignMedia(1L, photo, true)).thenReturn(media);
        assertThat(service.upload(1, photo)).isSameAs(media);
        var sequence = inOrder(authorization, storage);
        sequence.verify(authorization).requirePermission(PermissionName.MENU_MANAGE);
        sequence.verify(authorization).requireBranchAccess(1L);
        sequence.verify(storage).uploadCampaignMedia(1L, photo, true);
    }

    @Test
    void deniedPermissionCannotUpload() {
        doThrow(new IllegalArgumentException("Denied"))
                .when(authorization)
                .requirePermission(PermissionName.MENU_MANAGE);
        assertThatThrownBy(() -> service.upload(1, photo)).hasMessage("Denied");
        verifyNoInteractions(storage);
    }

    @Test
    void deniedBranchCannotUpload() {
        doThrow(new IllegalArgumentException("Wrong branch"))
                .when(authorization)
                .requireBranchAccess(2L);
        assertThatThrownBy(() -> service.upload(2, photo)).hasMessage("Wrong branch");
        verifyNoInteractions(storage);
    }

    @Test
    void invalidFilesPropagateSharedValidation() {
        when(storage.uploadCampaignMedia(1L, photo, true))
                .thenThrow(
                        new IllegalArgumentException(
                                "File content does not match its media type."));
        assertThatThrownBy(() -> service.upload(1, photo)).hasMessageContaining("File content");
    }
}
