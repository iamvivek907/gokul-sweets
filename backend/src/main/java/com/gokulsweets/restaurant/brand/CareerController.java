package com.gokulsweets.restaurant.brand;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;
@RestController @RequiredArgsConstructor
public class CareerController {
 private final CareerService service;
 @GetMapping("/api/storefront/careers") public List<CareerService.Job> publicJobs(@RequestParam(required=false) Long branchId){return service.publicJobs(branchId);}
 @PostMapping("/api/storefront/careers/applications") public CareerService.Receipt apply(@Valid @RequestBody CareerService.ApplicationInput input,HttpServletRequest request){return service.apply(input,request.getRemoteAddr());}
 @GetMapping("/api/admin/careers/jobs") public List<CareerService.Job> jobs(@RequestParam(required=false) Long branchId){return service.jobs(branchId);}
 @PostMapping("/api/admin/careers/jobs") public CareerService.Job create(@Valid @RequestBody CareerService.JobInput input){return service.create(input);}
 @PutMapping("/api/admin/careers/jobs/{id}") public CareerService.Job save(@PathVariable long id,@Valid @RequestBody CareerService.JobInput input,@RequestHeader("If-Match") long version){return service.save(id,input,version);}
 @GetMapping("/api/admin/careers/applications") public CareerService.Page applicants(@RequestParam(required=false) Long branchId,@RequestParam(required=false) Long jobId,@RequestParam(required=false) String status,@RequestParam(required=false) String search,@RequestParam(required=false) BigDecimal minExperience,@RequestParam(required=false) BigDecimal maxExperience,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.applicants(branchId,jobId,status,search,minExperience,maxExperience,page,size);}
 @GetMapping("/api/admin/careers/applications/{id}") public CareerService.Applicant detail(@PathVariable UUID id){return service.detail(id);}
 @PutMapping("/api/admin/careers/applications/{id}") public CareerService.Applicant update(@PathVariable UUID id,@Valid @RequestBody CareerService.Update input,@RequestHeader("If-Match") long version){return service.update(id,input,version);}
}
