package com.gokulsweets.restaurant.brand;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.storage.R2StorageService;

import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Coordinates brand operations. */
@Service
@RequiredArgsConstructor
public class BrandService {

    private final JdbcTemplate jdbc;

    private final StaffAuthorizationService staff;

    private final R2StorageService storage;

    /**
     * Immutable story data contract.
     *
     * @param title the title
     * @param subtitle the subtitle
     * @param storyTitle the story title
     * @param storyBody the story body
     * @param imageUrl the image url
     * @param published the published
     * @param version the version
     */
    public record Story(
            String title,
            String subtitle,
            String storyTitle,
            String storyBody,
            String imageUrl,
            boolean published,
            long version) {}

    /**
     * Immutable copy data contract.
     *
     * @param title the title
     * @param subtitle the subtitle
     * @param storyTitle the story title
     * @param storyBody the story body
     * @param published the published
     */
    public record Copy(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 400) String subtitle,
            @NotBlank @Size(max = 120) String storyTitle,
            @NotBlank @Size(max = 6000) String storyBody,
            boolean published) {}

    /**
     * Immutable person data contract.
     *
     * @param id the id
     * @param section the section
     * @param name the name
     * @param role the role
     * @param bio the bio
     * @param photoUrl the photo url
     * @param displayOrder the display order
     * @param published the published
     * @param version the version
     */
    public record Person(
            long id,
            String section,
            String name,
            String role,
            String bio,
            String photoUrl,
            int displayOrder,
            boolean published,
            long version) {}

    /**
     * Immutable person input data contract.
     *
     * @param section the section
     * @param name the name
     * @param role the role
     * @param bio the bio
     * @param displayOrder the display order
     * @param published the published
     */
    public record PersonInput(
            @Pattern(regexp = "FOUNDER|TEAM|DEVELOPER") @NotNull String section,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 120) String role,
            @NotNull @Size(max = 1500) String bio,
            @Min(0) @Max(10000) int displayOrder,
            boolean published) {}

    /**
     * Immutable content data contract.
     *
     * @param story the story
     * @param people the people
     */
    public record Content(Story story, List<Person> people) {}

    /**
     * Returns story information for brand.
     *
     * <p>Reads {@code brand_story}.
     *
     * @return the {@code Story} result
     */
    private Story story() {
        final long __gokulMethodStartedNanos = MethodTiming.start(BrandService.class, "story()");
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM brand_story WHERE id=1",
                    (r, n) ->
                            new Story(
                                    r.getString("title"),
                                    r.getString("subtitle"),
                                    r.getString("story_title"),
                                    r.getString("story_body"),
                                    r.getString("image_url"),
                                    r.getBoolean("published"),
                                    r.getLong("version")));
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BrandService.class, "story()");
        }
    }

    /**
     * Returns people information for brand.
     *
     * @param published the published supplied to this method
     * @return the {@code List<Person>} result
     */
    private List<Person> people(boolean published) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "people(boolean)");
        try {
            return jdbc.query(
                    "SELECT * FROM brand_people"
                            + (published ? " WHERE published" : "")
                            + " ORDER BY section,display_order,id",
                    (r, n) ->
                            new Person(
                                    r.getLong("id"),
                                    r.getString("section"),
                                    r.getString("name"),
                                    r.getString("role"),
                                    r.getString("bio"),
                                    r.getString("photo_url"),
                                    r.getInt("display_order"),
                                    r.getBoolean("published"),
                                    r.getLong("version")));
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BrandService.class, "people(boolean)");
        }
    }

    /**
     * Returns check information for brand.
     *
     * <p>Authorization checks include {@code PermissionName.ABOUT_MANAGE}.
     */
    private void check() {
        final long __gokulMethodStartedNanos = MethodTiming.start(BrandService.class, "check()");
        try {
            staff.requirePermission(PermissionName.ABOUT_MANAGE);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BrandService.class, "check()");
        }
    }

    /**
     * Returns changed information for brand.
     *
     * @param rows the rows supplied to this method
     * @throws ResponseStatusException when the method rejects the request with {@code Content
     *     changed. Reload before saving.}
     */
    private void changed(int rows) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "changed(int)");
        try {
            if (rows != 1)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Content changed. Reload before saving.");
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BrandService.class, "changed(int)");
        }
    }

    /**
     * Records an audit entry for brand data.
     *
     * <p>Writes {@code brand_career_audit}.
     *
     * @param action the action supplied to this method
     * @param id the id supplied to this method
     */
    private void audit(String action, long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "audit(String,long)");
        try {
            jdbc.update(
                    "INSERT INTO brand_career_audit(staff_id,action,entity_id) VALUES(?,?,?)",
                    staff.getCurrentStaff().getId(),
                    action,
                    String.valueOf(id));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BrandService.class, "audit(String,long)");
        }
    }

    /**
     * Publics content.
     *
     * @return the public content result
     */
    @Transactional(readOnly = true)
    public Content publicContent() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "publicContent()");
        try {
            var story = story();
            return new Content(story.published() ? story : null, people(true));
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BrandService.class, "publicContent()");
        }
    }

    /**
     * Admins content.
     *
     * @return the admin content result
     */
    @Transactional(readOnly = true)
    public Content adminContent() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "adminContent()");
        try {
            check();
            return new Content(story(), people(false));
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BrandService.class, "adminContent()");
        }
    }

    /**
     * Persists brand data and returns the {@code Story} result.
     *
     * <p>Writes {@code brand_story}.
     *
     * @param copy the copy supplied to this method
     * @param version the version supplied to this method
     * @return the value of {@code story()}
     */
    @Transactional
    public Story save(Copy copy, long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "save(Copy,long)");
        try {
            check();
            changed(
                    jdbc.update(
                            "UPDATE brand_story SET"
                                + " title=?,subtitle=?,story_title=?,story_body=?,published=?,version=version+1"
                                + " WHERE id=1 AND version=?",
                            copy.title().trim(),
                            copy.subtitle().trim(),
                            copy.storyTitle().trim(),
                            copy.storyBody().trim(),
                            copy.published(),
                            version));
            audit("ABOUT_COPY", 1);
            return story();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BrandService.class, "save(Copy,long)");
        }
    }

    /**
     * Creates brand data and returns the {@code Person} result.
     *
     * <p>Writes {@code brand_people}.
     *
     * @param input the input supplied to this method
     * @return the value of {@code person(id)}
     */
    @Transactional
    public Person create(PersonInput input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "create(PersonInput)");
        try {
            check();
            Long id =
                    jdbc.queryForObject(
                            "INSERT INTO"
                                    + " brand_people(section,name,role,bio,display_order,published)"
                                    + " VALUES(?,?,?,?,?,?) RETURNING id",
                            Long.class,
                            input.section(),
                            input.name().trim(),
                            input.role().trim(),
                            input.bio().trim(),
                            input.displayOrder(),
                            input.published());
            audit("PERSON_CREATE", id);
            return person(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BrandService.class, "create(PersonInput)");
        }
    }

    /**
     * Returns person information for brand.
     *
     * @param id the id supplied to this method
     * @return the {@code Person} result
     */
    private Person person(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "person(long)");
        try {
            return people(false).stream()
                    .filter(p -> p.id() == id)
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND, "Profile not found."));
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, BrandService.class, "person(long)");
        }
    }

    /**
     * Saves person.
     *
     * @param id the id
     * @param input the input
     * @param version the version
     * @return the save person result
     */
    @Transactional
    public Person savePerson(long id, PersonInput input, long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "savePerson(long,PersonInput,long)");
        try {
            check();
            changed(
                    jdbc.update(
                            "UPDATE brand_people SET"
                                + " section=?,name=?,role=?,bio=?,display_order=?,published=?,version=version+1"
                                + " WHERE id=? AND version=?",
                            input.section(),
                            input.name().trim(),
                            input.role().trim(),
                            input.bio().trim(),
                            input.displayOrder(),
                            input.published(),
                            id,
                            version));
            audit("PERSON_SAVE", id);
            return person(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BrandService.class,
                    "savePerson(long,PersonInput,long)");
        }
    }

    /**
     * Removes person.
     *
     * @param id the id
     * @param version the version
     */
    @Transactional
    public void removePerson(long id, long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "removePerson(long,long)");
        try {
            check();
            changed(jdbc.update("DELETE FROM brand_people WHERE id=? AND version=?", id, version));
            audit("PERSON_DELETE", id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BrandService.class, "removePerson(long,long)");
        }
    }

    /**
     * Uploads brand data and returns the {@code String} result.
     *
     * <p>Delegates to {@code storage.uploadCampaignMedia(...)}.
     *
     * @param id the id supplied to this method
     * @param file the file supplied to this method
     * @return the value of {@code media.url()}
     */
    private String upload(long id, MultipartFile file) {
        final long __gokulMethodStartedNanos_ =
                MethodTiming.start(BrandService.class, "upload(long,MultipartFile)");
        try {
            var media = storage.uploadCampaignMedia(id, file, true);
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {

                        /**
                         * Afters completion.
                         *
                         * @param status the status
                         */
                        @Override
                        public void afterCompletion(int status) {
                            final long __gokulMethodStartedNanos =
                                    MethodTiming.start(
                                            BrandService.class,
                                            "upload(long,MultipartFile)/anonymous[1]/afterCompletion(int)");
                            try {
                                if (status != STATUS_COMMITTED)
                                    storage.deleteCampaignMedia(media.url());
                            } finally {
                                MethodTiming.finish(
                                        __gokulMethodStartedNanos,
                                        BrandService.class,
                                        "upload(long,MultipartFile)/anonymous[1]/afterCompletion(int)");
                            }
                        }
                    });
            return media.url();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos_, BrandService.class, "upload(long,MultipartFile)");
        }
    }

    /**
     * Story photo.
     *
     * @param file the file
     * @param version the version
     * @return the story photo result
     */
    @Transactional
    public Story storyPhoto(MultipartFile file, long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "storyPhoto(MultipartFile,long)");
        try {
            check();
            String url = upload(0, file);
            changed(
                    jdbc.update(
                            "UPDATE brand_story SET image_url=?,version=version+1 WHERE id=1 AND"
                                    + " version=?",
                            url,
                            version));
            audit("ABOUT_PHOTO", 1);
            return story();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BrandService.class,
                    "storyPhoto(MultipartFile,long)");
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
    @Transactional
    public Person personPhoto(long id, MultipartFile file, long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "personPhoto(long,MultipartFile,long)");
        try {
            check();
            person(id);
            String url = upload(id, file);
            changed(
                    jdbc.update(
                            "UPDATE brand_people SET photo_url=?,version=version+1 WHERE id=? AND"
                                    + " version=?",
                            url,
                            id,
                            version));
            audit("PERSON_PHOTO", id);
            return person(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BrandService.class,
                    "personPhoto(long,MultipartFile,long)");
        }
    }

    /**
     * Removes story photo.
     *
     * @param version the version
     * @return the remove story photo result
     */
    @Transactional
    public Story removeStoryPhoto(long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "removeStoryPhoto(long)");
        try {
            check();
            changed(
                    jdbc.update(
                            "UPDATE brand_story SET image_url=NULL,version=version+1 WHERE id=1 AND"
                                    + " version=?",
                            version));
            audit("ABOUT_PHOTO_REMOVE", 1);
            return story();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BrandService.class, "removeStoryPhoto(long)");
        }
    }

    /**
     * Removes person photo.
     *
     * @param id the id
     * @param version the version
     * @return the remove person photo result
     */
    @Transactional
    public Person removePersonPhoto(long id, long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BrandService.class, "removePersonPhoto(long,long)");
        try {
            check();
            changed(
                    jdbc.update(
                            "UPDATE brand_people SET photo_url=NULL,version=version+1 WHERE id=?"
                                    + " AND version=?",
                            id,
                            version));
            audit("PERSON_PHOTO_REMOVE", id);
            return person(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BrandService.class, "removePersonPhoto(long,long)");
        }
    }
}
