package com.gokulsweets.restaurant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class RestaurantBackendApplication {

	public static void main(String[] args) {
		// Legacy entity callbacks still use LocalDateTime.now() without a zone.
		// Set the JVM business zone before Spring, scheduling and JDBC initialize.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(RestaurantBackendApplication.class, args);
	}

}
