package io.github.tiagoborja.canirun.games;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TierMatcherTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "NVIDIA GeForce RTX 5050 Laptop GPU | nvidia geforce rtx 5050 laptop gpu",
            "Intel(R) Core(TM) i7-6700 CPU @ 3.40GHz | intel core i7 6700 cpu 3 40ghz",
            "INTEL(r) CORE(tm) I5 | intel core i5",
            "AMD Ryzen 7 170 with Radeon Graphics | amd ryzen 7 170 with radeon graphics",
            "RTX_3060-Ti | rtx 3060 ti",
            "Radeon 680M | radeon 680m" })
    void shouldNormalizeModelWhenItHasMixedCaseAndPunctuation(String model, String expected) {
        assertThat(TierMatcher.normalize(model)).isEqualTo(expected);
    }

    @Test
    void shouldCollapseAndTrimSpacesWhenNormalizing() {
        assertThat(TierMatcher.normalize("  a \t  b\n\nc  ")).isEqualTo("a b c");
    }

    @Test
    void shouldReturnEmptyStringWhenModelIsEmptyOrOnlySeparators() {
        assertThat(TierMatcher.normalize("")).isEmpty();
        assertThat(TierMatcher.normalize(" - / (R)(TM) ")).isEmpty();
    }

    @Test
    void shouldReturnSameResultWhenNormalizingTwice() {
        String once = TierMatcher.normalize("Intel(R) Core(TM) i7-6700 CPU @ 3.40GHz");
        assertThat(TierMatcher.normalize(once)).isEqualTo(once);
    }

    @Test
    void shouldMatchKeyWhenItIsInsideModelWords() {
        assertThat(TierMatcher.match("AMD Ryzen 7 170 with Radeon Graphics", List.of("ryzen 7 170", "ryzen 5 5600")))
                .hasValue("ryzen 7 170");
    }

    @Test
    void shouldMatchKeyWhenModelHasRegisteredAndTradeMarkSigns() {
        assertThat(TierMatcher.match("Intel(R) Core(TM) i7-6700 CPU @ 3.40GHz", List.of("i7 6700", "i5 6600")))
                .hasValue("i7 6700");
    }

    @Test
    void shouldPreferLaptopKeyWhenBothLaptopAndDesktopKeysMatch() {
        assertThat(TierMatcher.match("NVIDIA GeForce RTX 5050 Laptop GPU", List.of("rtx 5050", "rtx 5050 laptop")))
                .hasValue("rtx 5050 laptop");
        assertThat(TierMatcher.match("NVIDIA GeForce RTX 5050 Laptop GPU", List.of("rtx 5050 laptop", "rtx 5050")))
                .hasValue("rtx 5050 laptop");
    }

    @Test
    void shouldFallBackToDesktopKeyWhenNoLaptopKeyExists() {
        assertThat(TierMatcher.match("NVIDIA GeForce RTX 5050 Laptop GPU", List.of("rtx 5050", "rtx 5060")))
                .hasValue("rtx 5050");
    }

    @Test
    void shouldReturnLongestKeyWhenSeveralKeysMatch() {
        List<String> keys = List.of("rtx 3060", "rtx 3060 ti", "rtx 3");
        assertThat(TierMatcher.match("NVIDIA GeForce RTX 3060 Ti", keys)).hasValue("rtx 3060 ti");
        assertThat(TierMatcher.match("NVIDIA GeForce RTX 3060", keys)).hasValue("rtx 3060");
    }

    @Test
    void shouldNotMatchWhenKeyIsOnlyAPrefixOfAWord() {
        assertThat(TierMatcher.match("NVIDIA GeForce GTX 10600", List.of("gtx 1060"))).isEmpty();
    }

    @Test
    void shouldNotMatchWhenKeyIsOnlyASuffixOfAWord() {
        assertThat(TierMatcher.match("NVIDIA GeForce XGTX 1060", List.of("gtx 1060"))).isEmpty();
    }

    @Test
    void shouldMatchWhenKeyIsAtTheStartOrTheEndOfTheModel() {
        assertThat(TierMatcher.match("gtx 1060 6gb", List.of("gtx 1060"))).hasValue("gtx 1060");
        assertThat(TierMatcher.match("nvidia gtx 1060", List.of("gtx 1060"))).hasValue("gtx 1060");
        assertThat(TierMatcher.match("gtx 1060", List.of("gtx 1060"))).hasValue("gtx 1060");
    }

    @Test
    void shouldMatchSuffixedModelWhenKeyIsTheBaseModelAndSuffixIsSeparateWord() {
        assertThat(TierMatcher.match("GeForce GTX 1060 6GB", List.of("gtx 1060", "gtx 1060 ti")))
                .hasValue("gtx 1060");
    }

    @Test
    void shouldNotMatchWhenSuffixIsGluedToTheModelNumber() {
        assertThat(TierMatcher.match("Intel Core i7-6700K", List.of("i7 6700"))).isEmpty();
    }

    @Test
    void shouldMatchWhenModelIsOnlyTheKeyInOtherCase() {
        assertThat(TierMatcher.match("Radeon 680M", List.of("radeon 680m"))).hasValue("radeon 680m");
    }

    @Test
    void shouldReturnEmptyWhenModelIsNull() {
        assertThat(TierMatcher.match(null, List.of("gtx 1060"))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenModelIsEmptyOrBlank() {
        assertThat(TierMatcher.match("", List.of("gtx 1060"))).isEmpty();
        assertThat(TierMatcher.match("   ", List.of("gtx 1060"))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNoKeyMatches() {
        assertThat(TierMatcher.match("Some Unknown GPU", List.of("gtx 1060", "rtx 3060"))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenThereAreNoKeys() {
        assertThat(TierMatcher.match("NVIDIA GeForce GTX 1060", List.of())).isEmpty();
    }
}
