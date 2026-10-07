package com.gokulsweets.restaurant.inventory.centre;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/inventory/branches/{branch}/centre")
public class InventoryCentreController {
 private final InventoryCentreJobs jobs;
 @PostMapping("/jobs") public Map<String,Object> submit(@PathVariable long branch,@Valid @RequestBody InventoryCentreJobs.Submit input){return jobs.submit(branch,input);}
 @GetMapping("/jobs") public List<Map<String,Object>> recent(@PathVariable long branch){return jobs.recent(branch);}
 @GetMapping("/jobs/{id}") public Map<String,Object> status(@PathVariable long branch,@PathVariable UUID id){return jobs.summary(branch,id);}
 @GetMapping("/jobs/{id}/results") public List<Map<String,Object>> results(@PathVariable long branch,@PathVariable UUID id,@RequestParam(defaultValue="0") int page){return jobs.results(branch,id,page);}
}
