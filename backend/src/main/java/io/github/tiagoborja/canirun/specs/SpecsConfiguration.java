package io.github.tiagoborja.canirun.specs;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SpecsConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
