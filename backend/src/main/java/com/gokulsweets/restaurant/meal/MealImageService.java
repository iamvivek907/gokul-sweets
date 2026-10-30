package com.gokulsweets.restaurant.meal;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.storage.R2StorageService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Uploads standalone meal artwork without changing any component product photo. */
@Service
@RequiredArgsConstructor
public class MealImageService {
    private final StaffAuthorizationService authorization;
    private final R2StorageService storage;

    /** Authorizes branch access before the shared static-image validation and upload. */
    public R2StorageService.CampaignMedia upload(long branchId, MultipartFile image) {
        long started = MethodTiming.start(MealImageService.class, "upload(long,MultipartFile)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            authorization.requireBranchAccess(branchId);
            return storage.uploadCampaignMedia(branchId, image, true);
        } finally {
            MethodTiming.finish(started, MealImageService.class, "upload(long,MultipartFile)");
        }
    }
}
