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
     * Apply the operation.
     *
     * @param input the input
     * @param request the request
     * @return the apply result
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
     * Jobses the operation.
     *
     * @param branchId the branch id
     * @return the jobs result
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
     * Creates the operation.
     *
     * @param input the input
     * @return the create result
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
     * Saves the operation.
     *
     * @param id the id
     * @param input the input
     * @param version the version
     * @return the save result
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
     * Applicantses the operation.
     *
     * @param branchId the branch id
     * @param jobId the job id
     * @param status the status
     * @param search the search
     * @param minExperience the min experience
     * @param maxExperience the max experience
     * @param page the page
     * @param size the size
     * @return the applicants result
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
     * Details the operation.
     *
     * @param id the id
     * @return the detail result
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
     * Updates the operation.
     *
     * @param id the id
     * @param input the input
     * @param version the version
     * @return the update result
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
