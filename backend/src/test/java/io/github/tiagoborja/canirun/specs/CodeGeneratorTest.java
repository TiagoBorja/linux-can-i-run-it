package io.github.tiagoborja.canirun.specs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    @Test
    void shouldReturnTenCharsFromCrockfordAlphabetWhenNextIsCalled() {
        CodeGenerator generator = new CodeGenerator();
        for (int i = 0; i < 10000; i++) {
            String code = generator.next();
            assertThat(code).hasSize(10);
            assertThat(code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)).as(code).isTrue();
        }
    }

    @Test
    void shouldReturnNoDuplicatesWhenNextIsCalled10000Times() {
        CodeGenerator generator = new CodeGenerator();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            codes.add(generator.next());
        }
        assertThat(codes).hasSize(10000);
    }
}
