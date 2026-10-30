package io.github.tiagoborja.canirun.games;

import io.github.tiagoborja.canirun.ApiError;
import io.github.tiagoborja.canirun.specs.SpecReportRequest;
import io.github.tiagoborja.canirun.specs.SpecsLookup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Games")
@RestController
class GamesController {

	private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
	private static final int SLUG_MAX = 100;

	private final GameRepository repository;
	private final SpecsLookup specs;

	GamesController(GameRepository repository, SpecsLookup specs) {
		this.repository = repository;
		this.specs = specs;
	}

	@Operation(summary = "List the games, sorted by name")
	@ApiResponse(responseCode = "200", description = "All games", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = GameSummary.class))))
	@ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	@GetMapping(path = "/api/games", produces = MediaType.APPLICATION_JSON_VALUE)
	List<GameSummary> list() {
		return repository.findAll();
	}

	@Operation(summary = "Compare a stored report with the requirements of a game")
	@ApiResponse(responseCode = "200", description = "Result for the minimum and recommended requirements", content = @Content(mediaType = "application/json", schema = @Schema(implementation = GameComparison.class)))
	@ApiResponse(responseCode = "404", description = "SPECS_NOT_FOUND (code is malformed, unknown or expired) or GAME_NOT_FOUND (slug is malformed or unknown)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	@ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
	@GetMapping(path = "/api/specs/{code}/games/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
	GameComparison compare(@PathVariable String code, @PathVariable String slug) {
		SpecReportRequest report = specs.find(code);
		if (slug.length() > SLUG_MAX || !SLUG.matcher(slug).matches()) {
			throw new GameNotFoundException();
		}
		GameSummary game = repository.findBySlug(slug).orElseThrow(GameNotFoundException::new);
		Map<String, Requirement> requirements = repository.findRequirements(slug);
		List<Tier> cpuTiers = repository.cpuTiers();
		List<Tier> gpuTiers = repository.gpuTiers();
		return new GameComparison(game,
				GameComparer.compare(report, requirements.get("minimum"), cpuTiers, gpuTiers),
				GameComparer.compare(report, requirements.get("recommended"), cpuTiers, gpuTiers));
	}
}
