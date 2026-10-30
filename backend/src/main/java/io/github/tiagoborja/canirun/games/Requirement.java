package io.github.tiagoborja.canirun.games;

/** One level (minimum or recommended) of a game. A null field means the game states no requirement. */
record Requirement(String cpuKey, String gpuKey, Integer vramMb, Integer ramMb, Integer diskMb,
		String vulkanVersion) {
}
