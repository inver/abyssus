package net.nevinsky.abyssus.uitesting.screenshot

import java.util.Properties

/**
 * What the pixels depend on: OS and architecture, JBR, IDE build, theme, scaling, locale, timezone, screen and font
 * packages. Baselines are only valid in the environment recorded next to them, so a comparison or update first checks
 * the running IDE against that manifest and refuses to continue on a difference.
 */
class ScreenshotEnvironment(val values: Map<String, String>) {

    /** `key: expected != actual` for every key of this manifest the [observed] environment does not match. */
    fun differences(observed: Map<String, String>): List<String> =
        values.filter { (key, expected) -> observed[key] != expected }.map { (key, expected) -> "$key: expected '$expected' but found '${observed[key]}'" }

    fun describe(): String = values.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }

    companion object {
        fun parse(text: String): ScreenshotEnvironment {
            val props = Properties().apply { load(text.reader()) }
            return ScreenshotEnvironment(props.stringPropertyNames().associateWith { props.getProperty(it).trim() })
        }

        /** Parses `key=value` lines such as `/etc/abyssus-ui-environment`. */
        fun parseLines(text: String): Map<String, String> =
            text.lineSequence().map { it.trim() }.filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    }
}
