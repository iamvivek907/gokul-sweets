package com.gokulsweets.restaurant.customer.consent;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/privacy-requests")
@RequiredArgsConstructor
public class AdminPrivacyRequestController {
    private final AdminPrivacyRequestQueue queue;

    @GetMapping
    @PreAuthorize("hasAuthority('PRIVACY_REQUEST_VIEW')")
    public ResponseEntity<List<AdminPrivacyRequestQueue.Entry>> list(@RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queue.view(page));
    }
}
