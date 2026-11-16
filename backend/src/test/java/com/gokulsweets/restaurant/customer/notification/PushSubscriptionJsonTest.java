package com.gokulsweets.restaurant.customer.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.gokulsweets.restaurant.staff.notification.StaffPushSubscriptions;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

class PushSubscriptionJsonTest {

    private static final UUID ACCOUNT = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID SETUP = UUID.fromString("b0000000-0000-4000-8000-000000000002");
    private static final JsonMapper JSON =
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    @ParameterizedTest
    @CsvSource({"missing,false", "null,false", "false,false", "true,true"})
    void customerSubscriptionDefaultsToRegistering(String flag, boolean pause) {
        var input =
                JSON.readValue(
                        requestBody("\"expectedSubject\":\"" + ACCOUNT + "\"", flag),
                        CustomerAlertPreferences.SubscriptionInput.class);

        assertThat(input.expectedSubject()).isEqualTo(ACCOUNT);
        assertThat(input.pausePrevious()).isEqualTo(pause);
    }

    @ParameterizedTest
    @CsvSource({"missing,false", "null,false", "false,false", "true,true"})
    void staffSubscriptionDefaultsToRegistering(String flag, boolean pause) {
        var input =
                JSON.readValue(
                        requestBody("\"expectedStaffId\":7,\"setupId\":\"" + SETUP + "\"", flag),
                        StaffPushSubscriptions.Input.class);

        assertThat(input.expectedStaffId()).isEqualTo(7L);
        assertThat(input.setupId()).isEqualTo(SETUP);
        assertThat(input.pausePrevious()).isEqualTo(pause);
    }

    private static String requestBody(String identity, String pausePrevious) {
        String flag = "missing".equals(pausePrevious) ? "" : ",\"pausePrevious\":" + pausePrevious;
        return "{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/test\",\"publicKey\":\"key\","
                + "\"authSecret\":\"auth\","
                + identity
                + flag
                + "}";
    }
}
