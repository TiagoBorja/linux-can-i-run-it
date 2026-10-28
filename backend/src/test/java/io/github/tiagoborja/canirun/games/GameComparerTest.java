package io.github.tiagoborja.canirun.games;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tiagoborja.canirun.specs.SpecReportRequest;
import io.github.tiagoborja.canirun.specs.SpecReportRequest.Cpu;
import io.github.tiagoborja.canirun.specs.SpecReportRequest.Distro;
import io.github.tiagoborja.canirun.specs.SpecReportRequest.Gpu;
import io.github.tiagoborja.canirun.specs.SpecReportRequest.Vulkan;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GameComparerTest {

    private static final List<Tier> CPU_TIERS = List.of(
            new Tier("i3 6100", "Intel Core i3-6100", 20),
            new Tier("i7 6700", "Intel Core i7-6700", 40),
            new Tier("ryzen 7 170", "AMD Ryzen 7 170", 60));
    private static final List<Tier> GPU_TIERS = List.of(
            new Tier("radeon 680m", "AMD Radeon 680M", 25),
            new Tier("gtx 1060", "NVIDIA GeForce GTX 1060 6GB", 30),
            new Tier("rtx 5050 laptop", "NVIDIA GeForce RTX 5050 Laptop", 70));

    private static final String RYZEN = "AMD Ryzen 7 170 with Radeon Graphics";
    private static final String I3 = "Intel(R) Core(TM) i3-6100 CPU @ 3.70GHz";
    private static final String I7 = "Intel(R) Core(TM) i7-6700 CPU @ 3.40GHz";
    private static final String RTX = "NVIDIA GeForce RTX 5050 Laptop GPU";
    private static final String RADEON = "Radeon 680M";
    private static final String UNKNOWN_MODEL = "Totally Unknown Model 9000";

    // ---- builders -------------------------------------------------------------------------------

    private static SpecReportRequest report(String cpuModel, List<Gpu> gpus, Integer ramMb, Integer diskFreeMb,
            Vulkan vulkan) {
        return new SpecReportRequest(1, new Cpu(cpuModel, null, null), gpus, ramMb, diskFreeMb,
                new Distro(null, null, null), null, vulkan);
    }

    private static SpecReportRequest emptyReport() {
        return report(null, List.of(), null, null, new Vulkan(null, null));
    }

    private static SpecReportRequest withCpu(String model) {
        return report(model, List.of(), null, null, new Vulkan(null, null));
    }

    private static SpecReportRequest withGpus(Gpu... gpus) {
        return report(null, List.of(gpus), null, null, new Vulkan(null, null));
    }

    private static SpecReportRequest withRam(Integer ramMb) {
        return report(null, List.of(), ramMb, null, new Vulkan(null, null));
    }

    private static SpecReportRequest withDisk(Integer diskFreeMb) {
        return report(null, List.of(), null, diskFreeMb, new Vulkan(null, null));
    }

    private static SpecReportRequest withVulkan(Boolean supported, String apiVersion) {
        return report(null, List.of(), null, null, new Vulkan(supported, apiVersion));
    }

    private static Gpu gpu(String model, Integer vramMb) {
        return new Gpu("other", model, vramMb, null);
    }

    private static Requirement noRequirement() {
        return new Requirement(null, null, null, null, null, null);
    }

    private static Requirement cpuReq(String key) {
        return new Requirement(key, null, null, null, null, null);
    }

    private static Requirement gpuReq(String key) {
        return new Requirement(null, key, null, null, null, null);
    }

    private static Requirement vramReq(int mb) {
        return new Requirement(null, null, mb, null, null, null);
    }

    private static Requirement ramReq(int mb) {
        return new Requirement(null, null, null, mb, null, null);
    }

    private static Requirement diskReq(int mb) {
        return new Requirement(null, null, null, null, mb, null);
    }

    private static Requirement vulkanReq(String version) {
        return new Requirement(null, null, null, null, null, version);
    }

    private static LevelResult compare(SpecReportRequest report, Requirement requirement) {
        return GameComparer.compare(report, requirement, CPU_TIERS, GPU_TIERS);
    }

    // ---- null requirement -----------------------------------------------------------------------

    @Test
    void shouldPassEveryPartWithNullRequiredWhenRequirementIsEmpty() {
        LevelResult level = compare(emptyReport(), noRequirement());

        assertThat(level.result()).isEqualTo(Result.PASS);
        for (Part part : allParts(level.parts())) {
            assertThat(part.result()).isEqualTo(Result.PASS);
            assertThat(part.required()).isNull();
        }
    }

    @Test
    void shouldPassEveryPartWhenRequirementIsEmptyEvenIfReportIsBad() {
        SpecReportRequest bad = report(UNKNOWN_MODEL, List.of(gpu(UNKNOWN_MODEL, 1)), 1, 1, new Vulkan(false, null));

        LevelResult level = compare(bad, noRequirement());

        assertThat(level.result()).isEqualTo(Result.PASS);
        for (Part part : allParts(level.parts())) {
            assertThat(part.result()).isEqualTo(Result.PASS);
            assertThat(part.required()).isNull();
        }
    }

    @Test
    void shouldReturnUnknownOnlyForPartsWithRequirementWhenReportIsEmpty() {
        Requirement req = new Requirement(null, null, null, 8192, null, "1.3");

        Parts parts = compare(emptyReport(), req).parts();

        assertThat(parts.cpu().result()).isEqualTo(Result.PASS);
        assertThat(parts.gpu().result()).isEqualTo(Result.PASS);
        assertThat(parts.vram().result()).isEqualTo(Result.PASS);
        assertThat(parts.ram().result()).isEqualTo(Result.UNKNOWN);
        assertThat(parts.disk().result()).isEqualTo(Result.PASS);
        assertThat(parts.vulkan().result()).isEqualTo(Result.UNKNOWN);
        assertThat(parts.ram().required()).isEqualTo(8192);
        assertThat(parts.vulkan().required()).isEqualTo("1.3");
        assertThat(parts.cpu().required()).isNull();
    }

    private static List<Part> allParts(Parts p) {
        return List.of(p.cpu(), p.gpu(), p.vram(), p.ram(), p.disk(), p.vulkan());
    }

    // ---- required values ------------------------------------------------------------------------

    @Test
    void shouldReturnTierNamesAndRawValuesInRequired() {
        Requirement req = new Requirement("i7 6700", "gtx 1060", 6144, 12288, 71680, "1.3");

        Parts parts = compare(emptyReport(), req).parts();

        assertThat(parts.cpu().required()).isEqualTo("Intel Core i7-6700");
        assertThat(parts.gpu().required()).isEqualTo("NVIDIA GeForce GTX 1060 6GB");
        assertThat(parts.vram().required()).isEqualTo(6144);
        assertThat(parts.ram().required()).isEqualTo(12288);
        assertThat(parts.disk().required()).isEqualTo(71680);
        assertThat(parts.vulkan().required()).isEqualTo("1.3");
    }

    // ---- CPU ------------------------------------------------------------------------------------

    @Test
    void shouldPassCpuWhenUserTierIsHigher() {
        assertThat(compare(withCpu(RYZEN), cpuReq("i7 6700")).parts().cpu().result()).isEqualTo(Result.PASS);
    }

    @Test
    void shouldPassCpuWhenUserTierIsEqual() {
        assertThat(compare(withCpu(I7), cpuReq("i7 6700")).parts().cpu().result()).isEqualTo(Result.PASS);
    }

    @Test
    void shouldFailCpuWhenUserTierIsLower() {
        assertThat(compare(withCpu(I3), cpuReq("i7 6700")).parts().cpu().result()).isEqualTo(Result.FAIL);
    }

    @Test
    void shouldReturnUnknownCpuWhenModelIsNull() {
        assertThat(compare(withCpu(null), cpuReq("i7 6700")).parts().cpu().result()).isEqualTo(Result.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownCpuWhenModelIsNotInTierTable() {
        assertThat(compare(withCpu(UNKNOWN_MODEL), cpuReq("i7 6700")).parts().cpu().result())
                .isEqualTo(Result.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownCpuWhenModelOnlyAlmostMatchesAKey() {
        assertThat(compare(withCpu("Intel Core i7-6700K"), cpuReq("i7 6700")).parts().cpu().result())
                .isEqualTo(Result.UNKNOWN);
    }

    // ---- GPU ------------------------------------------------------------------------------------

    @Test
    void shouldPassGpuWhenUserTierIsHigher() {
        assertThat(compare(withGpus(gpu(RTX, 8151)), gpuReq("gtx 1060")).parts().gpu().result())
                .isEqualTo(Result.PASS);
    }

    @Test
    void shouldPassGpuWhenUserTierIsEqual() {
        assertThat(compare(withGpus(gpu("NVIDIA GeForce GTX 1060 6GB", 6144)), gpuReq("gtx 1060")).parts().gpu()
                .result()).isEqualTo(Result.PASS);
    }

    @Test
    void shouldFailGpuWhenUserTierIsLower() {
        assertThat(compare(withGpus(gpu(RADEON, 512)), gpuReq("gtx 1060")).parts().gpu().result())
                .isEqualTo(Result.FAIL);
    }

    @Test
    void shouldPassGpuWhenDiscreteGpuPassesAndIntegratedGpuFails() {
        assertThat(compare(withGpus(gpu(RADEON, 512), gpu(RTX, 8151)), gpuReq("gtx 1060")).parts().gpu().result())
                .isEqualTo(Result.PASS);
        assertThat(compare(withGpus(gpu(RTX, 8151), gpu(RADEON, 512)), gpuReq("gtx 1060")).parts().gpu().result())
                .isEqualTo(Result.PASS);
    }

    @Test
    void shouldFailGpuWhenEveryGpuHasATierBelowRequired() {
        assertThat(compare(withGpus(gpu(RADEON, 512), gpu("NVIDIA GeForce GTX 1060 6GB", 6144)),
                gpuReq("rtx 5050 laptop")).parts().gpu().result()).isEqualTo(Result.FAIL);
    }

    @Test
    void shouldReturnUnknownGpuWhenOneGpuFailsAndAnotherHasNoTier() {
        assertThat(compare(withGpus(gpu(RADEON, 512), gpu(UNKNOWN_MODEL, 4096)), gpuReq("gtx 1060")).parts().gpu()
                .result()).isEqualTo(Result.UNKNOWN);
    }

    @Test
    void shouldPassGpuWhenOneGpuHasNoTierButAnotherPasses() {
        assertThat(compare(withGpus(gpu(UNKNOWN_MODEL, 4096), gpu(RTX, 8151)), gpuReq("gtx 1060")).parts().gpu()
                .result()).isEqualTo(Result.PASS);
    }

    @Test
    void shouldReturnUnknownGpuWhenGpuModelIsNull() {
        assertThat(compare(withGpus(gpu(null, 4096)), gpuReq("gtx 1060")).parts().gpu().result())
                .isEqualTo(Result.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownGpuWhenThereAreNoGpus() {
        assertThat(compare(withGpus(), gpuReq("gtx 1060")).parts().gpu().result()).isEqualTo(Result.UNKNOWN);
    }

    // ---- VRAM -----------------------------------------------------------------------------------

    /** Columns: required MB, reported MB, expected. The pass limit is 0.9 x required, rounded up. */
    @ParameterizedTest
    @CsvSource({
            "10000, 10000, PASS",
            "10000, 9000, PASS",
            "10000, 8999, FAIL",
            "8192, 8192, PASS",
            "8192, 8151, PASS",
            "8192, 7373, PASS",
            "8192, 7372, FAIL",
            "8192, 6144, FAIL",
            "8192, 0, FAIL",
            "1000, 900, PASS",
            "1000, 899, FAIL",
            "100, 90, PASS",
            "100, 89, FAIL",
            "70, 63, PASS",
            "70, 62, FAIL",
            "10, 9, PASS",
            "10, 8, FAIL" })
    void shouldApplyTenPercentToleranceToVram(int required, int reported, Result expected) {
        assertThat(compare(withGpus(gpu(RTX, reported)), vramReq(required)).parts().vram().result())
                .isEqualTo(expected);
    }

    @Test
    void shouldPassVramWhenDiscreteGpuHasEnoughAndIntegratedGpuHasNot() {
        assertThat(compare(withGpus(gpu(RADEON, 512), gpu(RTX, 8151)), vramReq(8192)).parts().vram().result())
                .isEqualTo(Result.PASS);
        assertThat(compare(withGpus(gpu(RTX, 8151), gpu(RADEON, 512)), vramReq(8192)).parts().vram().result())
                .isEqualTo(Result.PASS);
    }

    @Test
    void shouldFailVramWhenNoGpuHasEnough() {
        assertThat(compare(withGpus(gpu(RADEON, 512), gpu(RTX, 4096)), vramReq(8192)).parts().vram().result())
                .isEqualTo(Result.FAIL);
    }

    @Test
    void shouldReturnUnknownVramWhenOneGpuHasNullVramAndNoneHasEnough() {
        assertThat(compare(withGpus(gpu(RADEON, 512), gpu(RTX, null)), vramReq(8192)).parts().vram().result())
                .isEqualTo(Result.UNKNOWN);
    }

    @Test
    void shouldPassVramWhenOneGpuHasNullVramAndAnotherHasEnough() {
        assertThat(compare(withGpus(gpu(RADEON, null), gpu(RTX, 8151)), vramReq(8192)).parts().vram().result())
                .isEqualTo(Result.PASS);
    }

    @Test
    void shouldReturnUnknownVramWhenThereAreNoGpus() {
        assertThat(compare(withGpus(), vramReq(8192)).parts().vram().result()).isEqualTo(Result.UNKNOWN);
    }

    // ---- RAM ------------------------------------------------------------------------------------

    /** Columns: required MB, reported MB, expected. The pass limit is 0.9 x required, rounded up. */
    @ParameterizedTest
    @CsvSource({
            "16384, 16384, PASS",
            "16384, 15163, PASS",
            "16384, 14746, PASS",
            "16384, 14745, FAIL",
            "16384, 8192, FAIL",
            "10000, 9000, PASS",
            "10000, 8999, FAIL",
            "1000, 900, PASS",
            "1000, 899, FAIL",
            "70, 63, PASS",
            "70, 62, FAIL" })
    void shouldApplyTenPercentToleranceToRam(int required, int reported, Result expected) {
        assertThat(compare(withRam(reported), ramReq(required)).parts().ram().result()).isEqualTo(expected);
    }

    @Test
    void shouldReturnUnknownRamWhenReportedRamIsNull() {
        assertThat(compare(withRam(null), ramReq(8192)).parts().ram().result()).isEqualTo(Result.UNKNOWN);
    }

    // ---- disk -----------------------------------------------------------------------------------

    /** Columns: required MB, free MB, expected. No tolerance. */
    @ParameterizedTest
    @CsvSource({
            "71680, 106261, PASS",
            "71680, 71680, PASS",
            "71680, 71679, FAIL",
            "10000, 9000, FAIL",
            "10000, 0, FAIL" })
    void shouldNotApplyToleranceToDisk(int required, int free, Result expected) {
        assertThat(compare(withDisk(free), diskReq(required)).parts().disk().result()).isEqualTo(expected);
    }

    @Test
    void shouldReturnUnknownDiskWhenFreeDiskIsNull() {
        assertThat(compare(withDisk(null), diskReq(71680)).parts().disk().result()).isEqualTo(Result.UNKNOWN);
    }

    // ---- Vulkan ---------------------------------------------------------------------------------

    /** Columns: supported, apiVersion, required version, expected. Only major.minor counts. */
    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "true, 1.3.275, 1.3, PASS",
            "true, 1.3, 1.3, PASS",
            "true, 1.3.0, 1.3, PASS",
            "true, 1.4.0, 1.3, PASS",
            "true, 2.0.0, 1.3, PASS",
            "true, 1.10.0, 1.3, PASS",
            "true, 1.2, 1.3, FAIL",
            "true, 1.2.999, 1.3, FAIL",
            "true, 0.9, 1.3, FAIL",
            "true, 1.0.5, 1.1, FAIL",
            "true, garbage, 1.3, UNKNOWN",
            "true, null, 1.3, UNKNOWN",
            "true, '', 1.3, UNKNOWN",
            "true, 1, 1.3, UNKNOWN",
            "true, 1.3.x, 1.3, UNKNOWN",
            "true, v1.3, 1.3, UNKNOWN",
            "true, 1.3.275.1, 1.3, UNKNOWN",
            "false, 1.3.275, 1.3, FAIL",
            "false, null, 1.3, FAIL",
            "null, null, 1.3, UNKNOWN",
            "null, 1.3.275, 1.3, UNKNOWN" })
    void shouldCompareVulkanMajorAndMinorVersion(Boolean supported, String apiVersion, String required,
            Result expected) {
        assertThat(compare(withVulkan(supported, apiVersion), vulkanReq(required)).parts().vulkan().result())
                .isEqualTo(expected);
    }

    // ---- level roll-up --------------------------------------------------------------------------

    @Test
    void shouldPassLevelWhenEveryPartPasses() {
        SpecReportRequest report = report(RYZEN, List.of(gpu(RADEON, 512), gpu(RTX, 8151)), 15163, 106261,
                new Vulkan(true, "1.3.275"));
        Requirement req = new Requirement("i7 6700", "gtx 1060", 6144, 12288, 71680, "1.3");

        LevelResult level = compare(report, req);

        assertThat(level.result()).isEqualTo(Result.PASS);
        for (Part part : allParts(level.parts())) {
            assertThat(part.result()).isEqualTo(Result.PASS);
        }
    }

    @Test
    void shouldFailLevelWhenOnePartFailsAndOthersPass() {
        SpecReportRequest report = report(RYZEN, List.of(gpu(RTX, 8151)), 15163, 100, new Vulkan(true, "1.3.275"));
        Requirement req = new Requirement("i7 6700", "gtx 1060", 6144, 12288, 71680, "1.3");

        LevelResult level = compare(report, req);

        assertThat(level.result()).isEqualTo(Result.FAIL);
        assertThat(level.parts().disk().result()).isEqualTo(Result.FAIL);
        assertThat(level.parts().cpu().result()).isEqualTo(Result.PASS);
    }

    @Test
    void shouldFailLevelWhenOnePartFailsAndAnotherIsUnknown() {
        SpecReportRequest report = report(null, List.of(), 100, null, new Vulkan(null, null));
        Requirement req = new Requirement("i7 6700", null, null, 12288, null, null);

        LevelResult level = compare(report, req);

        assertThat(level.parts().cpu().result()).isEqualTo(Result.UNKNOWN);
        assertThat(level.parts().ram().result()).isEqualTo(Result.FAIL);
        assertThat(level.result()).isEqualTo(Result.FAIL);
    }

    @Test
    void shouldReturnUnknownLevelWhenOnePartIsUnknownAndOthersPass() {
        SpecReportRequest report = report(null, List.of(), 15163, null, new Vulkan(null, null));
        Requirement req = new Requirement("i7 6700", null, null, 12288, null, null);

        LevelResult level = compare(report, req);

        assertThat(level.parts().cpu().result()).isEqualTo(Result.UNKNOWN);
        assertThat(level.parts().ram().result()).isEqualTo(Result.PASS);
        assertThat(level.result()).isEqualTo(Result.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownLevelWhenEveryPartIsUnknown() {
        Requirement req = new Requirement("i7 6700", "gtx 1060", 6144, 12288, 71680, "1.3");

        LevelResult level = compare(emptyReport(), req);

        assertThat(level.result()).isEqualTo(Result.UNKNOWN);
        for (Part part : allParts(level.parts())) {
            assertThat(part.result()).isEqualTo(Result.UNKNOWN);
        }
    }
}
