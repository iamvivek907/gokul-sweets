package com.gokulsweets.restaurant.review;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.review.ReviewDtos.UpsertReviewRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

class ReviewControllerTest {
    @Test
    void deniedVerifiedOrderCannotReadOrWriteReview() {
        var reviews = mock(CustomerReviewService.class);
        var access = mock(VerifiedOrderAccess.class);
        var request = new MockHttpServletRequest();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .when(access)
                .requireOrder("GKS-FOREIGN", request);
        var controller = new ReviewController(reviews, access);

        assertThatThrownBy(() -> controller.getReviewContext("GKS-FOREIGN", request))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(
                        () ->
                                controller.upsertReview(
                                        "GKS-FOREIGN",
                                        new UpsertReviewRequest(5, "Great", List.of()),
                                        request))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(reviews);
    }
}
