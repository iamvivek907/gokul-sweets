package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.security.dto.AdminAuthResponse;
import com.gokulsweets.restaurant.staff.StaffUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final StaffAuthorizationService
            staffAuthorizationService;

    @GetMapping("/me")
    public ResponseEntity<AdminAuthResponse> me() {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();

        return ResponseEntity.ok(
                AdminAuthResponse.from(
                        staff
                )
        );
    }
}