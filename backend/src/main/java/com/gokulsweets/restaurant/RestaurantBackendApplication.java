package com.gokulsweets.restaurant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RestaurantBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(RestaurantBackendApplication.class, args);
	}

}
