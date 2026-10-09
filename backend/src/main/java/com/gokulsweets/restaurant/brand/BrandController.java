package com.gokulsweets.restaurant.brand;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** HTTP endpoints for brand operations. */
@RestController
@RequiredArgsConstructor
public class BrandController {

    private final BrandService service;

    /**
     * Publics content.
     *
     * @return the public content result
     */
    @GetMapping("/api/storefront/about")
    public BrandService.Content publicContent() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandController.class, "publicContent()");
        try {
            return service.publicContent();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BrandController.class, "publicContent()");
        }
    }

    /**
     * Handles {@code GET /api/admin/about} for brand.
     *
     * <p>Delegates to {@code service.adminContent(...)}.
     *
     * @return the value of {@code service.adminContent()}
     */
    @GetMapping("/api/admin/about")
    public BrandService.Content admin() {
        final long __gokulMethodStartedNanos = MethodTiming.start(BrandController.class, "admin()");
        try {
            return service.adminContent();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BrandController.class, "admin()");
        }
    }

    /**
     * Handles {@code PUT /api/admin/about} for brand.
     *
     * <p>Delegates to {@code service.save(...)}.
     *
     * @param copy the copy supplied to this method
     * @param version the version supplied to this method
     * @return the value of {@code service.save(copy, version)}
     */
    @PutMapping("/api/admin/about")
    public BrandService.Story save(
            @Valid @RequestBody BrandService.Copy copy, @RequestHeader("If-Match") long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandController.class, "save(BrandService.Copy,long)");
        try {
            return service.save(copy, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BrandController.class,
                    "save(BrandService.Copy,long)");
        }
    }

    /**
     * Handles {@code POST /api/admin/about/photo} for brand.
     *
     * <p>Delegates to {@code service.storyPhoto(...)}.
     *
     * @param file the file supplied to this method
     * @param version the version supplied to this method
     * @return the value of {@code service.storyPhoto(file, version)}
     */
    @PostMapping("/api/admin/about/photo")
    public BrandService.Story photo(
            @RequestParam MultipartFile file, @RequestHeader("If-Match") long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandController.class, "photo(MultipartFile,long)");
        try {
            return service.storyPhoto(file, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BrandController.class, "photo(MultipartFile,long)");
        }
    }

    /**
     * Removes photo.
     *
     * @param version the version
     * @return the remove photo result
     */
    @DeleteMapping("/api/admin/about/photo")
    public BrandService.Story removePhoto(@RequestHeader("If-Match") long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandController.class, "removePhoto(long)");
        try {
            return service.removeStoryPhoto(version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BrandController.class, "removePhoto(long)");
        }
    }

    /**
     * Handles {@code POST /api/admin/about/people} for brand.
     *
     * <p>Delegates to {@code service.create(...)}.
     *
     * @param person the person supplied to this method
     * @return the value of {@code service.create(person)}
     */
    @PostMapping("/api/admin/about/people")
    public BrandService.Person create(@Valid @RequestBody BrandService.PersonInput person) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandController.class, "create(BrandService.PersonInput)");
        try {
            return service.create(person);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BrandController.class,
                    "create(BrandService.PersonInput)");
        }
    }

    /**
     * Saves person.
     *
     * @param id the id
     * @param person the person
     * @param version the version
     * @return the save person result
     */
    @PutMapping("/api/admin/about/people/{id}")
    public BrandService.Person savePerson(
            @PathVariable long id,
            @Valid @RequestBody BrandService.PersonInput person,
            @RequestHeader("If-Match") long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BrandController.class, "savePerson(long,BrandService.PersonInput,long)");
        try {
            return service.savePerson(id, person, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BrandController.class,
                    "savePerson(long,BrandService.PersonInput,long)");
        }
    }

    /**
     * Removes person.
     *
     * @param id the id
     * @param version the version
     */
    @DeleteMapping("/api/admin/about/people/{id}")
    public void removePerson(@PathVariable long id, @RequestHeader("If-Match") long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandController.class, "removePerson(long,long)");
        try {
            service.removePerson(id, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BrandController.class, "removePerson(long,long)");
        }
    }

    /**
     * Persons photo.
     *
     * @param id the id
     * @param file the file
     * @param version the version
     * @return the person photo result
     */
    @PostMapping("/api/admin/about/people/{id}/photo")
    public BrandService.Person personPhoto(
            @PathVariable long id,
            @RequestParam MultipartFile file,
            @RequestHeader("If-Match") long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandController.class, "personPhoto(long,MultipartFile,long)");
        try {
            return service.personPhoto(id, file, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BrandController.class,
                    "personPhoto(long,MultipartFile,long)");
        }
    }

    /**
     * Removes person photo.
     *
     * @param id the id
     * @param version the version
     * @return the remove person photo result
     */
    @DeleteMapping("/api/admin/about/people/{id}/photo")
    public BrandService.Person removePersonPhoto(
            @PathVariable long id, @RequestHeader("If-Match") long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandController.class, "removePersonPhoto(long,long)");
        try {
            return service.removePersonPhoto(id, version);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BrandController.class,
                    "removePersonPhoto(long,long)");
        }
    }
}
