package com.gokulsweets.restaurant.menu.workspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branch}/menu/workspace")
public class MenuWorkspaceController {
 private final MenuWorkspaceService service;
 @GetMapping public MenuWorkspaceService.Page list(@PathVariable long branch,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam(defaultValue="") String search,@RequestParam(required=false) Long category,@RequestParam(defaultValue="ALL") String filter,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.list(branch,date,search,category,filter,page,size);}
 public record Create(@NotNull @Valid MenuWorkspaceService.Details details,@NotNull @Size(min=1,max=50) List<@Positive Long> branchIds){}
 @PostMapping public Map<String,Long> create(@PathVariable long branch,@Valid @RequestBody Create input){return Map.of("productId",service.create(branch,input.details(),input.branchIds()));}
 @PutMapping("/{id}/details") public void details(@PathVariable long branch,@PathVariable long id,@Valid @RequestBody MenuWorkspaceService.Details input){service.editDetails(branch,id,input);}
 @PatchMapping("/{id}/branch") public void editBranch(@PathVariable long branch,@PathVariable long id,@Valid @RequestBody MenuWorkspaceService.BranchEdit input){service.editBranch(branch,id,input);}
 @DeleteMapping("/{id}") public void deleteItem(@PathVariable long branch,@PathVariable long id,@RequestParam @Min(0) long version){service.deleteBranchItem(branch,id,version);}
 @PostMapping(value="/{id}/image",consumes="multipart/form-data") public Map<String,String> image(@PathVariable long branch,@PathVariable long id,@RequestParam long version,@RequestParam MultipartFile image){return Map.of("imageUrl",service.image(branch,id,version,image,false));}
 @DeleteMapping("/{id}/image") public void remove(@PathVariable long branch,@PathVariable long id,@RequestParam long version){service.image(branch,id,version,null,true);}
 @PutMapping("/{id}/routine/{date}") public void routine(@PathVariable long branch,@PathVariable long id,@PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@Valid @RequestBody MenuWorkspaceService.Routine input){service.routine(branch,id,date,input);}
 @PutMapping("/{id}/stock/{date}") public void stock(@PathVariable long branch,@PathVariable long id,@PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@Valid @RequestBody MenuWorkspaceService.StockEdit input){service.stock(branch,id,date,input);}
}
