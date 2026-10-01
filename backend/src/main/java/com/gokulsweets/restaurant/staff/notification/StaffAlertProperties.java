package com.gokulsweets.restaurant.staff.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gokul.notifications.staff")
@Getter @Setter
public class StaffAlertProperties {
    private boolean schedulerEnabled = true;
    private boolean recurringPreparationReminders;
    private int repeatMinutes = 2;
    private int reminderMinutes = 10;
    private int escalationMinutes = 5;
    private boolean emailEnabled;
    private String emailRecipients = "{}";
    private String emailApiKey = "";
    private String emailFrom = "";
    private String emailReplyTo = "";
    private String emailTestRecipient = "";
    private String emailSubjectPrefix = "[Gokul Sweets]";
}
