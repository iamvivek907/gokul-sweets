package com.gokulsweets.restaurant.branch;

import com.gokulsweets.restaurant.branch.dto.BranchResponse;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Coordinates branch operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BranchService {

    private final BranchRepository branchRepository;

    private final JdbcTemplate jdbc;

    private final EnhancementProperties features;

    /** Public details drawn from published branch experience and pickup configuration. */
    private record Display(
            String image, String mobileImage, String alt, String description, boolean pickup) {}

    /** Reads display details for the selected branches in a single database round trip. */
    private Map<Long, Display> display(List<Long> ids) {
        long started = MethodTiming.start(BranchService.class, "display(List<Long>)");
        try {
            if (ids.isEmpty()) return Map.of();
            boolean experience = features.isBranchExperience();
            String marks = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            var result = new HashMap<Long, Display>();
            jdbc.query(
                    """
SELECT b.id,e.published_image_url,e.published_mobile_url,e.published_alt_text,e.published_description,
       COALESCE(p.enabled,false)
FROM branches b
LEFT JOIN branch_experience e ON e.branch_id=b.id AND e.published_revision>0 AND ?
LEFT JOIN branch_pickup_settings p ON p.branch_id=b.id
WHERE b.id IN (%s)
"""
                            .formatted(marks),
                    (org.springframework.jdbc.core.RowCallbackHandler)
                            rs ->
                                    result.put(
                                            rs.getLong(1),
                                            new Display(
                                                    rs.getString(2),
                                                    rs.getString(3),
                                                    rs.getString(4),
                                                    rs.getString(5),
                                                    rs.getBoolean(6))),
                    java.util.stream.Stream.concat(
                                    java.util.stream.Stream.of(experience), ids.stream())
                            .toArray());
            return result;
        } finally {
            MethodTiming.finish(started, BranchService.class, "display(List<Long>)");
        }
    }

    /** Builds the public branch response using its independently published details. */
    private BranchResponse response(Branch branch, Display display) {
        long started = MethodTiming.start(BranchService.class, "response(Branch,Display)");
        try {
            return BranchResponse.from(
                    branch,
                    display == null ? null : display.image(),
                    display == null ? null : display.mobileImage(),
                    display == null ? null : display.alt(),
                    display == null ? null : display.description(),
                    display != null && display.pickup());
        } finally {
            MethodTiming.finish(started, BranchService.class, "response(Branch,Display)");
        }
    }

    /**
     * Returns active branches.
     *
     * @return the get active branches result
     */
    @Transactional(readOnly = true)
    public List<BranchResponse> getActiveBranches() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchService.class, "getActiveBranches()");
        try {
            var active = branchRepository.findByActiveTrueOrderByNameAsc();
            var details = display(active.stream().map(Branch::getId).toList());
            List<BranchResponse> branches =
                    active.stream()
                            .map(branch -> response(branch, details.get(branch.getId())))
                            .toList();
            log.debug("Loaded active customer branches: count={}", branches.size());
            return branches;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchService.class, "getActiveBranches()");
        }
    }

    /**
     * Returns branch.
     *
     * @param id the id
     * @return the get branch result
     */
    @Transactional(readOnly = true)
    public BranchResponse getBranch(Long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchService.class, "getBranch(Long)");
        try {
            if (id == null) {
                throw new IllegalArgumentException("Branch ID is required.");
            }
            Branch branch =
                    branchRepository
                            .findByIdAndActiveTrue(id)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "Customer requested unavailable branch:"
                                                        + " branchId={}",
                                                id);
                                        return new IllegalArgumentException(
                                                "Selected branch is unavailable.");
                                    });
            return response(branch, display(List.of(id)).get(id));
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BranchService.class, "getBranch(Long)");
        }
    }
}
