package net.nevinsky.abyssus.lib.gdx.assets

import java.util.UUID

/** A metadata identifier, or null when absent or malformed. Only UUID parse failures are swallowed. */
fun parseUuidOrNull(text: String?): UUID? = if (text == null) null else try {
    UUID.fromString(text)
} catch (_: IllegalArgumentException) {
    null
}
