package io.github.tiagoborja.canirun.specs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

@Configuration
class SpecsConfiguration {

	@Bean
	OpenAPI openApi() {
		return new OpenAPI().info(new Info().title("Can I Run It - Linux API").version("1")
				.description("Store anonymous Linux hardware reports and read them again by code."));
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	/**
	 * Rejects a number or boolean where the report needs a string, for example "kernel": 123. The
	 * application.yml settings reject the other wrong types.
	 */
	@Bean
	JsonMapperBuilderCustomizer strictTextCoercion() {
		return builder -> builder.withCoercionConfig(LogicalType.Textual, config -> config
				.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
	}
}
