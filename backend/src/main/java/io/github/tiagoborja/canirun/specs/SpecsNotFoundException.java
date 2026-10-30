package io.github.tiagoborja.canirun.specs;

public class SpecsNotFoundException extends RuntimeException {

	SpecsNotFoundException() {
		super("Specs report not found", null, false, false);
	}
}
