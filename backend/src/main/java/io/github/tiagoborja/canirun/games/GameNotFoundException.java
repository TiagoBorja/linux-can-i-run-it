package io.github.tiagoborja.canirun.games;

public class GameNotFoundException extends RuntimeException {

	GameNotFoundException() {
		super("Game not found", null, false, false);
	}
}
