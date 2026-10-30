package io.github.tiagoborja.canirun.games;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

final class TierMatcher {

	private TierMatcher() {
	}

	/** Lower case, (r) and (tm) removed, every other non-alphanumeric run becomes one space. */
	static String normalize(String model) {
		return model.toLowerCase(Locale.ROOT).replace("(r)", "").replace("(tm)", "")
				.replaceAll("[^a-z0-9]+", " ").trim();
	}

	/** The longest key that is in the model as whole words. Empty for a null model or no match. */
	static Optional<String> match(String model, Collection<String> keys) {
		if (model == null) {
			return Optional.empty();
		}
		String words = " " + normalize(model) + " ";
		return keys.stream().filter(key -> words.contains(" " + key + " "))
				.max(Comparator.comparingInt(String::length));
	}
}
