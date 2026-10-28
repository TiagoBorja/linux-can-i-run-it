package io.github.tiagoborja.canirun.specs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TextValidatorTest {

    private final TextValidator validator = new TextValidator();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "abc", "😀", "a😀b", "😀😀" })
    void shouldAcceptWhenTextHasNoLoneSurrogate(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "\uD800", "\uDC00", "a\uD800", "\uDC00a", "a\uD800b", "a\uDC00b", "\uD800😀",
            "😀\uDC00", "\uDE00\uD83D" })
    void shouldRejectWhenTextHasLoneSurrogate(String value) {
        assertThat(validator.isValid(value, null)).isFalse();
    }
}
