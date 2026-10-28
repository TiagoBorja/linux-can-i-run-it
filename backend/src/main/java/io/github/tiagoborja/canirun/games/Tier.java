package io.github.tiagoborja.canirun.games;

/** One row of cpu_tier or gpu_tier. The key is a normalized model string. */
record Tier(String key, String name, int tier) {
}
