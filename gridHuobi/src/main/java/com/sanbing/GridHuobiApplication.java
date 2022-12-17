package com.sanbing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class GridHuobiApplication {

	public static void main(String[] args) {
		SpringApplication.run(GridHuobiApplication.class, args);
	}

}
