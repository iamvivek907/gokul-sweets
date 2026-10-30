package com.gokulsweets.restaurant.meal;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.storage.R2StorageService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Staff-only uploads for both new and existing meal drafts. */
@RestController
@RequiredArgsConstructor
public class MealImageController {
    private final MealImageService images;

    /** Returns the permanent image URL; saving the meal publishes the selected photo. */
    @PostMapping(
            value = "/api/admin/branches/{branchId}/meals/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R2StorageService.CampaignMedia upload(
            @PathVariable long branchId, @RequestParam MultipartFile image) {
        long started = MethodTiming.start(MealImageController.class, "upload(long,MultipartFile)");
        try {
            return images.upload(branchId, image);
        } finally {
            MethodTiming.finish(started, MealImageController.class, "upload(long,MultipartFile)");
        }
    }
}
