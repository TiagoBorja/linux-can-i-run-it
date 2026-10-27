package io.github.tiagoborja.canirun.specs;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Makes random 10-character codes (Crockford base32, 50 bits). */
@Component
public class CodeGenerator {

	private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
	static final int LENGTH = 10;

	private final SecureRandom random = new SecureRandom();

	public String next() {
		char[] chars = new char[LENGTH];
		for (int i = 0; i < LENGTH; i++) {
			chars[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
		}
		return new String(chars);
	}
}
