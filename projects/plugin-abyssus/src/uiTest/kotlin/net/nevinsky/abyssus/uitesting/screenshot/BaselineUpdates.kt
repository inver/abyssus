package net.nevinsky.abyssus.uitesting.screenshot

import java.nio.file.Path

/** How screenshot checks behave: compare against baselines, update selected baselines, or only collect captures. */
enum class ScreenshotMode { COMPARE, UPDATE, OFF;
    companion object {
        fun parse(text: String?): ScreenshotMode = when (text?.lowercase()) {
            null, "", "compare" -> COMPARE
            "update" -> UPDATE
            "off" -> OFF
            else -> error("unknown screenshot mode '$text' (compare, update, off)")
        }
    }
}

/** Why an update is refused, or null when it may proceed. Pure, so the rules are unit-tested. */
class UpdatePolicy(private val ci: Boolean, private val selected: Set<String>) {
    fun refusal(scenario: String, environmentDifferences: List<String>): String? = when {
        ci -> "baseline updates never run on CI"
        environmentDifferences.isNotEmpty() ->
            "refusing to update $scenario outside the canonical environment: ${environmentDifferences.joinToString("; ")}"
        else -> null
    }

    /** Only the scenarios named for update are rewritten; the rest are compared as usual. */
    fun selects(scenario: String): Boolean = scenario in selected
}

/**
 * Captures to write as baselines. Nothing touches the baseline folder until [commit], which a scenario calls only after
 * its behavioural checks passed; [discard] leaves every old image alone.
 */
class PendingBaselines(private val artifacts: ScreenshotArtifacts, private val baselineDir: Path) {
    private val pending = LinkedHashMap<String, Pixels>()

    fun add(scenario: String, pixels: Pixels) {
        pending[scenario] = pixels
    }

    val scenarios: Set<String> get() = pending.keys

    fun commit() {
        pending.forEach { (scenario, pixels) -> artifacts.write(baselineDir.resolve("$scenario.png"), pixels) }
        pending.clear()
    }

    fun discard() = pending.clear()
}
