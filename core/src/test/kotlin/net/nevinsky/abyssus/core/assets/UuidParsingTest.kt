package net.nevinsky.abyssus.core.assets

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class UuidParsingTest {
    @Test fun validIdentifiersKeepTheirValueAndUnreadableOnesAreAbsent() {
        assertEquals(UUID(0, 1), parseUuidOrNull("00000000-0000-0000-0000-000000000001"))
        for (text in listOf(null, "", "nope", "00000000-0000-0000-0000-00000000000x")) {
            assertNull(text, parseUuidOrNull(text))
        }
    }
}
