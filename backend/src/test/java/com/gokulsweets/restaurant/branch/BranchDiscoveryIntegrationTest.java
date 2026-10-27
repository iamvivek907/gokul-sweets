package com.gokulsweets.restaurant.branch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@SpringBootTest
@Transactional
class BranchDiscoveryIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BranchOfferingsService offerings;
    @Autowired BranchDiscoveryService discovery;
    @MockitoBean StaffAuthorizationService staff;

    String key() {
        return UUID.randomUUID().toString();
    }

    long branch() {
        return jdbc.queryForObject(
                "INSERT INTO branches(code,name) VALUES (?,?) RETURNING id",
                Long.class,
                key(),
                "Discovery branch");
    }

    long slot(long branch) {
        return jdbc.queryForObject(
                "INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity) VALUES"
                        + " (?,CURRENT_DATE,'10:00','11:00',50) RETURNING id",
                Long.class,
                branch);
    }

    long product(long branch, String name, boolean available, boolean occasion) {
        long cat =
                jdbc.queryForObject(
                        "INSERT INTO categories(code,name) VALUES (?,?) RETURNING id",
                        Long.class,
                        key(),
                        "Discovery category");
        long tax =
                jdbc.queryForObject(
                        "INSERT INTO tax_categories(code,name) VALUES (?,?) RETURNING id",
                        Long.class,
                        key(),
                        "Discovery tax");
        long p =
                jdbc.queryForObject(
                        "INSERT INTO products(code,category_id,tax_category_id,name,base_price)"
                                + " VALUES (?,?,?,?,100) RETURNING id",
                        Long.class,
                        key(),
                        cat,
                        tax,
                        name);
        jdbc.update(
                "INSERT INTO branch_products(branch_id,product_id,available,occasion_only) VALUES"
                        + " (?,?,?,?)",
                branch,
                p,
                available,
                occasion);
        return p;
    }

    void review(
            long branch,
            long slot,
            long p,
            int overall,
            int item,
            String status,
            String comment,
            String orderStatus) {
        long order =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " orders(order_number,branch_id,pickup_slot_id,customer_name,customer_phone,pickup_type,order_status,reservation_expires_at)"
                            + " VALUES (?,?,?,'Private"
                            + " customer','9876543210','NORMAL',?,CURRENT_TIMESTAMP) RETURNING id",
                        Long.class,
                        key(),
                        branch,
                        slot,
                        orderStatus);
        long review =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " reviews(order_id,branch_id,overall_rating,comment,review_status,created_at,updated_at)"
                            + " VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING"
                            + " id",
                        Long.class,
                        order,
                        branch,
                        overall,
                        comment,
                        status);
        jdbc.update(
                "INSERT INTO"
                    + " review_items(review_id,product_id,product_name,rating,created_at,updated_at)"
                    + " VALUES (?,?,'Historical name',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                review,
                p,
                item);
    }

    @Test
    void offeringsAreBranchScopedDraftPrivateVersionedAndCanBeRemoved() {
        long id = branch(), other = branch();
        var first =
                offerings.save(
                        id,
                        new BranchOfferingsService.Input(
                                List.of(
                                        new BranchOfferingsService.Offering(
                                                "A real speciality", "Configured by the branch"))),
                        0);
        assertThat(offerings.published(id)).isEmpty();
        assertThat(offerings.published(other)).isEmpty();
        assertThatThrownBy(() -> offerings.publish(id, 0))
                .isInstanceOf(ResponseStatusException.class);
        var live = offerings.publish(id, first.version());
        assertThat(offerings.published(id).getFirst().title()).isEqualTo("A real speciality");
        var empty = offerings.save(id, new BranchOfferingsService.Input(List.of()), live.version());
        assertThat(offerings.published(id)).hasSize(1);
        offerings.publish(id, empty.version());
        assertThat(offerings.published(id)).isEmpty();
        verify(staff, atLeastOnce()).requirePermission(PermissionName.BRANCH_MANAGE);
        doThrow(new AccessDeniedException("Other branch")).when(staff).requireBranchAccess(other);
        assertThatThrownBy(() -> offerings.admin(other)).isInstanceOf(AccessDeniedException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM branch_offering_settings WHERE branch_id=?",
                                Long.class,
                                other))
                .isZero();
    }

    @Test
    void publishedCompletedExperienceAndItemRatingsNeverMixBranchesOrHiddenReviews() {
        long b = branch(), s = slot(b), p = product(b, "Top rated sweet", true, false);
        review(
                b,
                s,
                p,
                5,
                4,
                "PUBLISHED",
                "Fresh and carefully packed for our pickup.",
                "PICKED_UP");
        review(
                b,
                s,
                p,
                3,
                2,
                "PUBLISHED",
                "The overall pickup experience was reasonable.",
                "PICKED_UP");
        review(
                b,
                s,
                p,
                1,
                1,
                "HIDDEN",
                "This hidden review must never appear publicly.",
                "PICKED_UP");
        review(
                b,
                s,
                p,
                1,
                1,
                "PUBLISHED",
                "An unfinished order must not affect public ratings.",
                "CONFIRMED");
        long other = branch();
        review(
                other,
                slot(other),
                p,
                1,
                1,
                "PUBLISHED",
                "Another branch must not affect these ratings.",
                "PICKED_UP");
        var result = discovery.get(b);
        assertThat(discovery.reviews(b, null).reviews())
                .extracting(BranchDiscoveryService.PublicReview::comment)
                .containsExactlyInAnyOrder(
                        "Fresh and carefully packed for our pickup.",
                        "The overall pickup experience was reasonable.");
        assertThat(result.overallExperience().average()).isEqualTo(4);
        assertThat(result.overallExperience().count()).isEqualTo(2);
        assertThat(result.overallExperience().distribution()).containsExactly(0L, 0L, 1L, 0L, 1L);
        assertThat(result.topRatedItems()).hasSize(1);
        var top = result.topRatedItems().getFirst();
        assertThat(top.name()).isEqualTo("Top rated sweet");
        assertThat(top.average()).isEqualTo(3);
        assertThat(top.count()).isEqualTo(2);
        assertThat(top.review().comment()).doesNotContain("hidden", "unfinished", "Another branch");
        jdbc.update(
                "UPDATE branch_products SET available=false WHERE branch_id=? AND product_id=?",
                b,
                p);
        assertThat(discovery.get(b).topRatedItems()).isEmpty();
        assertThat(discovery.get(b).overallExperience().count()).isEqualTo(2);
    }

    @Test
    void emptyBranchesAndOccasionOnlyItemsDoNotInventRatings() {
        long b = branch();
        assertThat(discovery.get(b).overallExperience().count()).isZero();
        assertThat(discovery.get(b).topRatedItems()).isEmpty();
        assertThat(discovery.reviews(b, null).reviews()).isEmpty();
        long p = product(b, "Occasion exclusive", true, true);
        review(
                b,
                slot(b),
                p,
                5,
                5,
                "PUBLISHED",
                "A thoughtful and meaningful review of the order.",
                "PICKED_UP");
        assertThat(discovery.get(b).topRatedItems()).isEmpty();
        jdbc.update("UPDATE branches SET active=false WHERE id=?", b);
        assertThatThrownBy(() -> discovery.get(b)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void recentCommentsAreBoundedAndExcludePrivateContactAndUnpublishedOrders() {
        long b = branch(), s = slot(b), p = product(b, "Sweet", true, false);
        for (int i = 0; i < 7; i++)
            review(
                    b,
                    s,
                    p,
                    5,
                    5,
                    "PUBLISHED",
                    "A pleasant completed order number " + i,
                    "DELIVERED");
        review(
                b,
                s,
                p,
                2,
                2,
                "PUBLISHED",
                "Please contact me on 9876543210 about this",
                "PICKED_UP");
        review(
                b,
                s,
                p,
                2,
                2,
                "PUBLISHED",
                "Write to test@example.com about this order",
                "PICKED_UP");
        review(b, s, p, 2, 2, "PUBLISHED", "Too short", "PICKED_UP");
        var comments = discovery.reviews(b, null).reviews();
        assertThat(comments).hasSize(7);
        assertThat(comments)
                .allSatisfy(
                        comment -> {
                            assertThat(comment.comment())
                                    .doesNotContain("9876543210", "example.com");
                            assertThat(comment.overallRating()).isEqualTo(5);
                        });
    }

    @Test
    void publicReviewPagesUseBoundedCursorAndExplicitNameChoice() {
        long b = branch(), s = slot(b), p = product(b, "Sweet", true, false);
        for (int i = 0; i < 25; i++)
            review(b, s, p, 5, 5, "PUBLISHED", "A lovely review of order number " + i, "PICKED_UP");
        jdbc.update(
                "UPDATE reviews SET show_customer_name=true WHERE id=(SELECT MAX(id) FROM reviews"
                        + " WHERE branch_id=?)",
                b);
        var first = discovery.reviews(b, null);
        assertThat(first.reviews()).hasSize(20);
        assertThat(first.reviews().getFirst().displayName()).isEqualTo("Private");
        assertThat(first.reviews().get(1).displayName()).isEqualTo("Customer");
        assertThat(first.nextBefore()).isNotNull();
        var second = discovery.reviews(b, first.nextBefore());
        assertThat(second.reviews()).hasSize(5);
        assertThat(second.nextBefore()).isNull();
        assertThat(second.reviews())
                .extracting(BranchDiscoveryService.PublicReview::comment)
                .doesNotContainAnyElementsOf(
                        first.reviews().stream()
                                .map(BranchDiscoveryService.PublicReview::comment)
                                .toList());
        assertThatThrownBy(() -> discovery.reviews(b, 0L))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(BranchDiscoveryService.publicName(true, "Riya Chaurasiya")).isEqualTo("Riya");
        assertThat(BranchDiscoveryService.publicName(false, "Riya Chaurasiya"))
                .isEqualTo("Customer");
        assertThat(BranchDiscoveryService.publicName(true, "9876543210")).isEqualTo("Customer");
    }

    @Test
    void shortOrContactBearingCommentsAreNotPublishedAsQuotes() {
        assertThat(BranchDiscoveryService.publicComment("Nice")).isNull();
        assertThat(
                        BranchDiscoveryService.publicComment(
                                "Contact me at name@example.com for more information"))
                .isNull();
        assertThat(BranchDiscoveryService.publicComment("My phone is 9876543210 and this was good"))
                .isNull();
        assertThat(
                        BranchDiscoveryService.publicComment(
                                " Fresh sweets\nwith careful packing and good service. "))
                .isEqualTo("Fresh sweets with careful packing and good service.");
    }
}
