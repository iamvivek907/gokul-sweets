package com.gokulsweets.restaurant.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {
    @RestController
    static class RejectedRequest {
        @GetMapping("/test-response-failure")
        public String disconnected(@RequestParam boolean disconnect) {
            throw new org.springframework.http.converter.HttpMessageNotWritableException(
                    "Could not write JSON",
                    disconnect
                            ? new org.springframework.web.context.request.async
                                    .AsyncRequestNotUsableException(
                                    "Response failed", new java.io.IOException("Broken pipe"))
                            : new IllegalStateException("Broken serializer"));
        }

        @GetMapping("/test-rejection")
        public String reject(@RequestParam int status) {
            throw new ResponseStatusException(HttpStatus.valueOf(status), "Review required");
        }
    }

    @Test
    void disconnectedResponseDoesNotAttemptAnotherBodyButRealSerializationFailuresRemainErrors()
            throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new RejectedRequest())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        mvc.perform(get("/test-response-failure").param("disconnect", "true"))
                .andExpect(content().string(""));
        mvc.perform(get("/test-response-failure").param("disconnect", "false"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void upstreamAndDatabaseFailuresWithSimilarMessagesAreNotSilenced() {
        var handler = new GlobalExceptionHandler();
        var request =
                new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/payments");
        var databaseFailure =
                new org.springframework.http.converter.HttpMessageNotWritableException(
                        "Lazy read failed",
                        new org.springframework.dao.DataAccessResourceFailureException(
                                "Broken pipe", new java.io.IOException("Broken pipe")));
        var upstreamFailure =
                new org.springframework.http.converter.HttpMessageNotWritableException(
                        "Gateway failed",
                        new org.springframework.web.client.RestClientException("Broken pipe"));
        for (var failure : java.util.List.of(databaseFailure, upstreamFailure)) {
            org.assertj.core.api.Assertions.assertThat(
                            handler.handleUnexpected(failure, request).getStatusCode())
                    .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    void deliberateSessionAndValidationFailuresKeepTheirStatusAndCustomerSafeReason()
            throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new RejectedRequest())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        for (int code : new int[] {400, 401, 404, 409, 429, 503}) {
            mvc.perform(get("/test-rejection").param("status", Integer.toString(code)))
                    .andExpect(status().is(code))
                    .andExpect(jsonPath("$.status").value(code))
                    .andExpect(jsonPath("$.message").value("Review required"))
                    .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
        }
    }
}
