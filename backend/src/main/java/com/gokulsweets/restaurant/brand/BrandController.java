package com.gokulsweets.restaurant.brand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequiredArgsConstructor
public class BrandController {
 private final BrandService service;
 @GetMapping("/api/storefront/about") public BrandService.Content publicContent(){return service.publicContent();}
 @GetMapping("/api/admin/about") public BrandService.Content admin(){return service.adminContent();}
 @PutMapping("/api/admin/about") public BrandService.Story save(@Valid @RequestBody BrandService.Copy copy,@RequestHeader("If-Match") long version){return service.save(copy,version);}
 @PostMapping("/api/admin/about/photo") public BrandService.Story photo(@RequestParam MultipartFile file,@RequestHeader("If-Match") long version){return service.storyPhoto(file,version);}
 @DeleteMapping("/api/admin/about/photo") public BrandService.Story removePhoto(@RequestHeader("If-Match") long version){return service.removeStoryPhoto(version);}
 @PostMapping("/api/admin/about/people") public BrandService.Person create(@Valid @RequestBody BrandService.PersonInput person){return service.create(person);}
 @PutMapping("/api/admin/about/people/{id}") public BrandService.Person savePerson(@PathVariable long id,@Valid @RequestBody BrandService.PersonInput person,@RequestHeader("If-Match") long version){return service.savePerson(id,person,version);}
 @DeleteMapping("/api/admin/about/people/{id}") public void removePerson(@PathVariable long id,@RequestHeader("If-Match") long version){service.removePerson(id,version);}
 @PostMapping("/api/admin/about/people/{id}/photo") public BrandService.Person personPhoto(@PathVariable long id,@RequestParam MultipartFile file,@RequestHeader("If-Match") long version){return service.personPhoto(id,file,version);}
 @DeleteMapping("/api/admin/about/people/{id}/photo") public BrandService.Person removePersonPhoto(@PathVariable long id,@RequestHeader("If-Match") long version){return service.removePersonPhoto(id,version);}
}
