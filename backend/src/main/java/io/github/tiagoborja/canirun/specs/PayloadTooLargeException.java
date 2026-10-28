package io.github.tiagoborja.canirun.specs;

import java.io.IOException;

class PayloadTooLargeException extends IOException {

	PayloadTooLargeException() {
		super("Payload too large", null);
	}
}
