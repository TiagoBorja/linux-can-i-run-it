package io.github.tiagoborja.canirun.games;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class GameRepository {

	private static final RowMapper<Tier> TIER = (rs, i) -> new Tier(rs.getString("key"), rs.getString("name"),
			rs.getInt("tier"));
	private static final RowMapper<GameSummary> GAME = (rs, i) -> new GameSummary(rs.getString("slug"),
			rs.getString("name"));

	private final JdbcTemplate jdbc;

	GameRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// shortcut: no paging, add it when the seed passes a few hundred games.
	List<GameSummary> findAll() {
		return jdbc.query("select slug, name from game order by name", GAME);
	}

	Optional<GameSummary> findBySlug(String slug) {
		return jdbc.query("select slug, name from game where slug = ?", GAME, slug).stream().findFirst();
	}

	/** The requirements of a game by level (minimum, recommended). */
	Map<String, Requirement> findRequirements(String slug) {
		return jdbc.query("select level, cpu_key, gpu_key, vram_mb, ram_mb, disk_mb, vulkan_version "
				+ "from game_requirement where game_slug = ?", rs -> {
					Map<String, Requirement> result = new HashMap<>();
					while (rs.next()) {
						result.put(rs.getString("level"),
								new Requirement(rs.getString("cpu_key"), rs.getString("gpu_key"),
										rs.getObject("vram_mb", Integer.class), rs.getObject("ram_mb", Integer.class),
										rs.getObject("disk_mb", Integer.class), rs.getString("vulkan_version")));
					}
					return result;
				}, slug);
	}

	// shortcut: loads every tier row per request, cache them when the tables grow past a few hundred rows.
	List<Tier> cpuTiers() {
		return jdbc.query("select key, name, tier from cpu_tier", TIER);
	}

	List<Tier> gpuTiers() {
		return jdbc.query("select key, name, tier from gpu_tier", TIER);
	}
}
