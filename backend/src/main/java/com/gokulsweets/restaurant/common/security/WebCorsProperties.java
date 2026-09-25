package com.gokulsweets.restaurant.common.security;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    // The deployment environment supplies the exact customer origin. In the
    // absence of that setting, only local development is allowed to use the API.
    @NotEmpty
    private List<@NotBlank @Pattern(regexp = "https://[^/\\s*]+(?::[0-9]+)?|http://localhost(?::[0-9]+)?") String>
            allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));
}
