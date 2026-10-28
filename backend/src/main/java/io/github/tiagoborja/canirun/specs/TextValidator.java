package io.github.tiagoborja.canirun.specs;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Rejects an unpaired high or low surrogate. Postgres jsonb rejects it. */
class TextValidator implements ConstraintValidator<Text, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isHighSurrogate(c) && i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
				i++;
			} else if (Character.isSurrogate(c)) {
				return false;
			}
		}
		return true;
	}
}
