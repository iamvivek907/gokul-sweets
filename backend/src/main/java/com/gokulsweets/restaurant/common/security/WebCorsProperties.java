package com.gokulsweets.restaurant.common.security;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "gokul.web")
@Validated
@Getter
@Setter
public class WebCorsProperties {

    @NotEmpty
    private List<String> allowedOrigins = new ArrayList<>(
            List.of(
                    "http://localhost:3000",
                    "https://gokul-sweets-dev.vercel.app",
                    "https://dev.gokulsweets.in",
                    "https://gokulsweets.in"
            )
    );
}