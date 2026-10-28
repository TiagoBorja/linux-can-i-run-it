package io.github.tiagoborja.canirun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CanIRunItApplication {

	public static void main(String[] args) {
		SpringApplication.run(CanIRunItApplication.class, args);
	}
}
