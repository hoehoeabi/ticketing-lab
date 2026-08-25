package com.ticketing.ticketing_lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class TicketingLabApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketingLabApplication.class, args);
	}

}
