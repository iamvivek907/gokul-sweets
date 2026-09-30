package com.gokulsweets.restaurant.customer.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gokul.notifications.web-push")
@Getter
@Setter
public class WebPushProperties {
    private boolean schedulerEnabled = true;
    private String publicKey = "";
    private String privateKey = "";
    private String subject = "";
}
