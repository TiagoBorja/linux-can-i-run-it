package io.github.tiagoborja.canirun.games;

import io.github.tiagoborja.canirun.specs.SpecReportRequest;
import io.github.tiagoborja.canirun.specs.SpecReportRequest.Gpu;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Pure function: report + requirement + tier tables give a result per part. */
final class GameComparer {

	private static final Pattern VERSION = Pattern.compile("^(\\d{1,9})\\.(\\d{1,9})(\\.\\d{1,9})?$");

	private GameComparer() {
	}

	static LevelResult compare(SpecReportRequest report, Requirement req, List<Tier> cpuTiers, List<Tier> gpuTiers) {
		Parts parts = new Parts(cpu(report, req, cpuTiers), gpu(report, req, gpuTiers), vram(report, req),
				ram(report, req), disk(report, req), vulkan(report, req));
		List<Result> results = List.of(parts.cpu().result(), parts.gpu().result(), parts.vram().result(),
				parts.ram().result(), parts.disk().result(), parts.vulkan().result());
		Result level = results.contains(Result.FAIL) ? Result.FAIL
				: results.contains(Result.UNKNOWN) ? Result.UNKNOWN : Result.PASS;
		return new LevelResult(level, parts);
	}

	private static Part cpu(SpecReportRequest report, Requirement req, List<Tier> tiers) {
		if (req.cpuKey() == null) {
			return none();
		}
		Tier needed = byKey(tiers, req.cpuKey());
		Result result = tierOf(report.cpu().model(), tiers).map(mine -> of(mine.tier() >= needed.tier()))
				.orElse(Result.UNKNOWN);
		return new Part(result, needed.name());
	}

	private static Part gpu(SpecReportRequest report, Requirement req, List<Tier> tiers) {
		if (req.gpuKey() == null) {
			return none();
		}
		Tier needed = byKey(tiers, req.gpuKey());
		List<Optional<Tier>> mine = report.gpus().stream().map(g -> tierOf(g.model(), tiers)).toList();
		Result result;
		if (mine.stream().anyMatch(t -> t.isPresent() && t.get().tier() >= needed.tier())) {
			result = Result.PASS;
		} else if (mine.isEmpty() || mine.stream().anyMatch(Optional::isEmpty)) {
			result = Result.UNKNOWN;
		} else {
			result = Result.FAIL;
		}
		return new Part(result, needed.name());
	}

	private static Part vram(SpecReportRequest report, Requirement req) {
		if (req.vramMb() == null) {
			return none();
		}
		List<Gpu> gpus = report.gpus();
		Result result;
		if (gpus.stream().anyMatch(g -> g.vramMb() != null && enough(g.vramMb(), req.vramMb()))) {
			result = Result.PASS;
		} else if (gpus.isEmpty() || gpus.stream().anyMatch(g -> g.vramMb() == null)) {
			result = Result.UNKNOWN;
		} else {
			result = Result.FAIL;
		}
		return new Part(result, req.vramMb());
	}

	private static Part ram(SpecReportRequest report, Requirement req) {
		if (req.ramMb() == null) {
			return none();
		}
		Integer have = report.ramMb();
		return new Part(have == null ? Result.UNKNOWN : of(enough(have, req.ramMb())), req.ramMb());
	}

	private static Part disk(SpecReportRequest report, Requirement req) {
		if (req.diskMb() == null) {
			return none();
		}
		Integer have = report.diskFreeMb();
		return new Part(have == null ? Result.UNKNOWN : of(have >= req.diskMb()), req.diskMb());
	}

	private static Part vulkan(SpecReportRequest report, Requirement req) {
		if (req.vulkanVersion() == null) {
			return none();
		}
		Boolean supported = report.vulkan().supported();
		Result result;
		if (supported == null) {
			result = Result.UNKNOWN;
		} else if (!supported) {
			result = Result.FAIL;
		} else {
			int[] have = version(report.vulkan().apiVersion());
			int[] needed = version(req.vulkanVersion());
			result = have == null || needed == null ? Result.UNKNOWN
					: of(have[0] != needed[0] ? have[0] > needed[0] : have[1] >= needed[1]);
		}
		return new Part(result, req.vulkanVersion());
	}

	/** MemTotal and reported VRAM are below the installed size, so 90% of the requirement passes. */
	private static boolean enough(int have, int required) {
		return have * 10L >= required * 9L;
	}

	/** {major, minor} of major.minor[.patch], or null if the text has another shape. */
	private static int[] version(String text) {
		Matcher m = text == null ? null : VERSION.matcher(text);
		return m != null && m.matches() ? new int[] { Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) }
				: null;
	}

	private static Optional<Tier> tierOf(String model, List<Tier> tiers) {
		Map<String, Tier> byKey = tiers.stream().collect(Collectors.toMap(Tier::key, Function.identity()));
		return TierMatcher.match(model, byKey.keySet()).map(byKey::get);
	}

	/** A key missing from the table cannot happen: the foreign keys forbid it. */
	private static Tier byKey(List<Tier> tiers, String key) {
		return tiers.stream().filter(t -> t.key().equals(key)).findFirst().orElseThrow();
	}

	private static Result of(boolean pass) {
		return pass ? Result.PASS : Result.FAIL;
	}

	private static Part none() {
		return new Part(Result.PASS, null);
	}
}
