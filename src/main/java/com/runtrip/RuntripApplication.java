package com.runtrip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RuntripApplication {

	public static void main(String[] args) {
		SpringApplication.run(RuntripApplication.class, args);
	}

}
