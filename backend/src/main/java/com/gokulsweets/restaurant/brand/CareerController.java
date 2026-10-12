package com.gokulsweets.restaurant.brand;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/** HTTP endpoints for career operations. */
@RestController
@RequiredArgsConstructor
public class CareerController {

    private final CareerService service;

    /**
     * Publics jobs.
     *
     * @param branchId the branch id
     * @return the public jobs result
     */
    @GetMapping("/api/storefront/careers")
    public List<CareerService.Job> publicJobs(@RequestParam(required = false) Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerController.class, "publicJobs(Long)");
        try {
            return service.publicJobs(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CareerController.class, "publicJobs(Long)");
        }
    }

    /**
     * Handles {@code POST /api/storefront/careers/applications} for career.
     *
     * <p>Delegates to {@code service.apply(...)}.
     *
     * @param input the input supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code service.apply(input, request.getRemoteAddr())}
     */
    @PostMapping("/api/storefront/careers/applications")
    public CareerService.Receipt apply(
            @Valid @RequestBody CareerService.ApplicationInput input, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CareerController.class,
                        "apply(CareerService.ApplicationInput,HttpServletRequest)");
        try {
            return service.apply(input, request.getRemoteAddr());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CareerController.class,
                    "apply(CareerService.ApplicationInput,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code GET /api/admin/careers/jobs} for career.
     *
     * <p>Delegates to {@code service.jobs(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code service.jobs(branchId)}
     */
    @GetMapping("/api/admin/careers/jobs")
    public List<CareerService.Job> jobs(@RequestParam(required = false) Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerController.class, "jobs(Long)");
        try {
            return service.jobs(branchId);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CareerController.class, "jobs(Long)");
        }
    }

    /**
     * Handles {@code POST /api/admin/careers/jobs} for career.
     *
     * <p>Delegates to {@code service.create(...)}.
     *
     * @param input the input supplied to this method
     * @return the value of {@code service.create(input)}
     */
    @PostMapping("/api/admin/careers/jobs")
    public CareerService.Job create(@Valid @RequestBody CareerService.JobInput input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerController.class, "create(CareerService.JobInput)");
        try {
            return service.create(input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CareerController.class,
                    "create(CareerService.JobInput)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/careers/jobs/{id}} for career.
     *
     * <p>Delegates to {@code service.save(...)}.
     *
     * @param id the id supplied to this method
     * @param input the input supplied to this method
     * @param version the version supplied to this method
     * @return the value of {@code service.save(id, input, version)}
     */
    @PutMapping("/api/admin/careers/jobs/{id}")
    public CareerService.Job save(
            @PathVariable long id,
            @Valid @RequestBody CareerService.JobInput input,
            @RequestHeader("If-Match") long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CareerController.class, "save(long,CareerService.JobInput,long)");
        try {
            return service.save(id, input, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CareerController.class,
                    "save(long,CareerService.JobInput,long)");
        }
    }

    /**
     * Handles {@code GET /api/admin/careers/applications} for career.
     *
     * <p>Delegates to {@code service.applicants(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param jobId the job id supplied to this method
     * @param status the status supplied to this method
     * @param search the search supplied to this method
     * @param minExperience the min experience supplied to this method
     * @param maxExperience the max experience supplied to this method
     * @param page the page supplied to this method
     * @param size the size supplied to this method
     * @return the value of {@code service.applicants(branchId, jobId, status, search,
     *     minExperience, maxExperience, page, size)}
     */
    @GetMapping("/api/admin/careers/applications")
    public CareerService.Page applicants(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal minExperience,
            @RequestParam(required = false) BigDecimal maxExperience,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CareerController.class,
                        "applicants(Long,Long,String,String,BigDecimal,BigDecimal,int,int)");
        try {
            return service.applicants(
                    branchId, jobId, status, search, minExperience, maxExperience, page, size);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CareerController.class,
                    "applicants(Long,Long,String,String,BigDecimal,BigDecimal,int,int)");
        }
    }

    /**
     * Handles {@code GET /api/admin/careers/applications/{id}} for career.
     *
     * <p>Delegates to {@code service.detail(...)}.
     *
     * @param id the id supplied to this method
     * @return the value of {@code service.detail(id)}
     */
    @GetMapping("/api/admin/careers/applications/{id}")
    public CareerService.Applicant detail(@PathVariable UUID id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CareerController.class, "detail(UUID)");
        try {
            return service.detail(id);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, CareerController.class, "detail(UUID)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/careers/applications/{id}} for career.
     *
     * <p>Delegates to {@code service.update(...)}.
     *
     * @param id the id supplied to this method
     * @param input the input supplied to this method
     * @param version the version supplied to this method
     * @return the value of {@code service.update(id, input, version)}
     */
    @PutMapping("/api/admin/careers/applications/{id}")
    public CareerService.Applicant update(
            @PathVariable UUID id,
            @Valid @RequestBody CareerService.Update input,
            @RequestHeader("If-Match") long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CareerController.class, "update(UUID,CareerService.Update,long)");
        try {
            return service.update(id, input, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CareerController.class,
                    "update(UUID,CareerService.Update,long)");
        }
    }
}
