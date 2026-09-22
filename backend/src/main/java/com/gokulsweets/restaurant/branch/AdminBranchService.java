package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchActiveRequest;
import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchCreateRequest;
import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchResponse;
import com.gokulsweets.restaurant.branch.dto.admin.AdminBranchUpdateRequest;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.staff.StaffUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBranchService {

    private final BranchRepository
            branchRepository;

    private final StaffAuthorizationService
            staffAuthorizationService;


    /*
     * =========================================================
     * LIST ADMIN BRANCHES
     * =========================================================
     */

    @Transactional(readOnly = true)
    public List<AdminBranchResponse> getBranches() {

        requireBranchManagePermission();


        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();


        List<Branch> branches;


        if (
                isOwnerAdmin(
                        staff
                )
        ) {

            branches =
                    branchRepository
                            .findAll();

        } else {

            /*
             * Non-owner administrators may only see branches
             * explicitly assigned through staff_branch_access.
             *
             * We copy the lazy collection while the transaction
             * is open and sort it deterministically.
             */
            branches =
                    staff.getBranches()
                            .stream()
                            .toList();
        }


        return branches
                .stream()
                .sorted(
                        Comparator
                                .comparing(
                                        Branch::getName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(
                                        Branch::getId
                                )
                )
                .map(
                        AdminBranchResponse::from
                )
                .toList();
    }


    /*
     * =========================================================
     * GET ADMIN BRANCH
     * =========================================================
     */

    @Transactional(readOnly = true)
    public AdminBranchResponse getBranch(
            Long branchId
    ) {

        requireBranchManagePermission();


        Branch branch =
                getBranchEntity(
                        branchId
                );


        staffAuthorizationService
                .requireBranchAccess(
                        branch.getId()
                );


        return AdminBranchResponse
                .from(
                        branch
                );
    }


    /*
     * =========================================================
     * CREATE BRANCH
     * =========================================================
     *
     * Creating a brand-new branch is owner-only.
     *
     * A normal branch manager cannot be granted access to a
     * branch that does not exist yet, so creation belongs to
     * OWNER_ADMIN.
     */

    @Transactional
    public AdminBranchResponse createBranch(
            AdminBranchCreateRequest request
    ) {

        requireBranchManagePermission();


        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();


        requireOwnerAdmin(
                staff
        );


        String code =
                normalizeCode(
                        request.code()
                );


        branchRepository
                .findByCode(
                        code
                )
                .ifPresent(
                        existing -> {

                            throw new IllegalArgumentException(
                                    "A branch with code "
                                            +
                                            code
                                            +
                                            " already exists."
                            );
                        }
                );


        validateCoordinates(
                request.latitude(),
                request.longitude()
        );


        validateOpeningHours(
                request.openingTime(),
                request.closingTime()
        );


        Branch branch =
                new Branch();


        branch.setCode(
                code
        );


        applyEditableFields(
                branch,
                request.name(),
                request.address(),
                request.city(),
                request.state(),
                request.pincode(),
                request.phone(),
                request.latitude(),
                request.longitude(),
                request.openingTime(),
                request.closingTime()
        );


        branch.setActive(
                request.active()
                        == null
                        ||
                        request.active()
        );


        Branch saved =
                branchRepository
                        .saveAndFlush(
                                branch
                        );


        log.info(
                "Branch created: branchId={}, code={}, createdByStaffUserId={}",
                saved.getId(),
                saved.getCode(),
                staff.getId()
        );


        return AdminBranchResponse
                .from(
                        saved
                );
    }


    /*
     * =========================================================
     * UPDATE BRANCH
     * =========================================================
     */

    @Transactional
    public AdminBranchResponse updateBranch(
            Long branchId,
            AdminBranchUpdateRequest request
    ) {

        requireBranchManagePermission();


        Branch branch =
                getBranchEntity(
                        branchId
                );


        staffAuthorizationService
                .requireBranchAccess(
                        branch.getId()
                );


        validateCoordinates(
                request.latitude(),
                request.longitude()
        );


        validateOpeningHours(
                request.openingTime(),
                request.closingTime()
        );


        applyEditableFields(
                branch,
                request.name(),
                request.address(),
                request.city(),
                request.state(),
                request.pincode(),
                request.phone(),
                request.latitude(),
                request.longitude(),
                request.openingTime(),
                request.closingTime()
        );


        Branch saved =
                branchRepository
                        .saveAndFlush(
                                branch
                        );


        log.info(
                "Branch updated: branchId={}, code={}, updatedByStaffUserId={}",
                saved.getId(),
                saved.getCode(),
                staffAuthorizationService
                        .getCurrentStaff()
                        .getId()
        );


        return AdminBranchResponse
                .from(
                        saved
                );
    }


    /*
     * =========================================================
     * ACTIVATE / DEACTIVATE
     * =========================================================
     */

    @Transactional
    public AdminBranchResponse updateActiveStatus(
            Long branchId,
            AdminBranchActiveRequest request
    ) {

        requireBranchManagePermission();


        Branch branch =
                getBranchEntity(
                        branchId
                );


        staffAuthorizationService
                .requireBranchAccess(
                        branch.getId()
                );


        branch.setActive(
                request.active()
        );


        Branch saved =
                branchRepository
                        .saveAndFlush(
                                branch
                        );


        log.info(
                "Branch active status changed: branchId={}, code={}, active={}, updatedByStaffUserId={}",
                saved.getId(),
                saved.getCode(),
                saved.isActive(),
                staffAuthorizationService
                        .getCurrentStaff()
                        .getId()
        );


        return AdminBranchResponse
                .from(
                        saved
                );
    }


    /*
     * =========================================================
     * ENTITY LOOKUP
     * =========================================================
     */

    private Branch getBranchEntity(
            Long branchId
    ) {

        if (
                branchId == null
        ) {

            throw new IllegalArgumentException(
                    "Branch ID is required."
            );
        }


        return branchRepository
                .findById(
                        branchId
                )
                .orElseThrow(
                        () -> {

                            log.warn(
                                    "Admin branch not found: branchId={}",
                                    branchId
                            );


                            return new IllegalArgumentException(
                                    "Branch does not exist."
                            );
                        }
                );
    }


    /*
     * =========================================================
     * PERMISSIONS
     * =========================================================
     */

    private void requireBranchManagePermission() {

        staffAuthorizationService
                .requirePermission(
                        PermissionName.BRANCH_MANAGE
                );
    }


    private boolean isOwnerAdmin(
            StaffUser staff
    ) {

        return staff.getRole()
                != null
                &&
                "OWNER_ADMIN".equals(
                        staff.getRole()
                                .getName()
                );
    }


    private void requireOwnerAdmin(
            StaffUser staff
    ) {

        if (
                !isOwnerAdmin(
                        staff
                )
        ) {

            log.warn(
                    "Non-owner attempted to create a branch: staffUserId={}",
                    staff.getId()
            );


            throw new AccessDeniedException(
                    "Only the owner administrator can create a branch."
            );
        }
    }


    /*
     * =========================================================
     * EDITABLE FIELDS
     * =========================================================
     */

    private void applyEditableFields(

            Branch branch,

            String name,

            String address,

            String city,

            String state,

            String pincode,

            String phone,

            BigDecimal latitude,

            BigDecimal longitude,

            java.time.LocalTime openingTime,

            java.time.LocalTime closingTime
    ) {

        branch.setName(
                normalizeRequiredText(
                        name,
                        "Branch name"
                )
        );


        branch.setAddress(
                normalizeNullableText(
                        address
                )
        );


        branch.setCity(
                normalizeNullableText(
                        city
                )
        );


        branch.setState(
                normalizeNullableText(
                        state
                )
        );


        branch.setPincode(
                normalizeNullableText(
                        pincode
                )
        );


        branch.setPhone(
                normalizeNullableText(
                        phone
                )
        );


        branch.setLatitude(
                latitude
        );


        branch.setLongitude(
                longitude
        );


        branch.setOpeningTime(
                openingTime
        );


        branch.setClosingTime(
                closingTime
        );
    }


    /*
     * =========================================================
     * VALIDATION
     * =========================================================
     */

    private void validateCoordinates(
            BigDecimal latitude,
            BigDecimal longitude
    ) {

        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException(
                    "Latitude and longitude must both be provided."
            );
        }

        if (
                latitude != null
                        && (
                        latitude.compareTo(
                                BigDecimal.valueOf(-90)
                        ) < 0
                                || latitude.compareTo(
                                BigDecimal.valueOf(90)
                        ) > 0
                )
        ) {
            throw new IllegalArgumentException(
                    "Latitude must be between -90 and 90."
            );
        }

        if (
                longitude != null
                        && (
                        longitude.compareTo(
                                BigDecimal.valueOf(-180)
                        ) < 0
                                || longitude.compareTo(
                                BigDecimal.valueOf(180)
                        ) > 0
                )
        ) {
            throw new IllegalArgumentException(
                    "Longitude must be between -180 and 180."
            );
        }
    }


    private void validateOpeningHours(
            java.time.LocalTime openingTime,
            java.time.LocalTime closingTime
    ) {

        if ((openingTime == null) != (closingTime == null)) {
            throw new IllegalArgumentException(
                    "Opening and closing time must both be provided."
            );
        }

        if (openingTime == null) {
            return;
        }

        if (openingTime.equals(closingTime)) {
            throw new IllegalArgumentException(
                    "Opening and closing time cannot be the same."
            );
        }

        /*
         * Closing time may be earlier than opening time for
         * branches that operate across midnight.
         *
         * Example: 18:00 to 02:00.
         */
    }


    /*
     * =========================================================
     * NORMALIZATION
     * =========================================================
     */

    private String normalizeCode(
            String value
    ) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Branch code is required."
            );
        }


        return value
                .trim()
                .toUpperCase();
    }


    private String normalizeRequiredText(

            String value,

            String fieldName
    ) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            throw new IllegalArgumentException(
                    fieldName
                            +
                            " is required."
            );
        }


        return value.trim();
    }


    private String normalizeNullableText(
            String value
    ) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            return null;
        }


        return value.trim();
    }
}