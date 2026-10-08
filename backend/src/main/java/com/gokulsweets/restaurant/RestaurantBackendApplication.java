package com.gokulsweets.restaurant;

import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/** Backend restaurant backend application contract and implementation. */
@SpringBootApplication
@EnableScheduling
public class RestaurantBackendApplication {

    /**
     * Mains the operation.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RestaurantBackendApplication.class, "main(String[])");
        try {
            // Legacy entity callbacks still use LocalDateTime.now() without a zone.
            // Set the JVM business zone before Spring, scheduling and JDBC initialize.
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            SpringApplication application =
                    new SpringApplication(RestaurantBackendApplication.class);
            StartupDiagnostics.configure(
                    application,
                    Boolean.parseBoolean(System.getenv("GOKUL_STARTUP_DIAGNOSTICS_ENABLED")));
            application.run(args);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RestaurantBackendApplication.class,
                    "main(String[])");
        }
    }
}
