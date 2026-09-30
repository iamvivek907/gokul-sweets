package com.gokulsweets.restaurant.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @RestController
    static class RejectedRequest {
        @GetMapping("/test-rejection")
        public String reject(@RequestParam int status) {
            throw new ResponseStatusException(HttpStatus.valueOf(status), "Review required");
        }
    }
    @Test
    void deliberateSessionAndValidationFailuresKeepTheirStatusAndCustomerSafeReason() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new RejectedRequest())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for(int code:new int[]{400,401,404,409,429,503}) {
            mvc.perform(get("/test-rejection").param("status",Integer.toString(code)))
                .andExpect(status().is(code)).andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.message").value("Review required"))
                .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
        }
    }
}
