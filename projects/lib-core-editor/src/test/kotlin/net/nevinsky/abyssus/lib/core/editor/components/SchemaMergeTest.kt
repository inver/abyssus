/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.parseScene
import org.junit.Assert.*
import org.junit.Test

/** The current editor models built-ins and leaves game components opaque. */
class SchemaMergeTest {
    @Test
    fun gameComponentsAreNeverOfferedByTheBuiltInEditor() {
        val editor = ComponentEditor(ResourceEditorMessages())
        val root =
            parseScene(
                """{"format":"abyssus","formatVersion":1,
"ecs":{"0":{"components":{"MarkerComponent":{"size":1.000}}}}}"""
            )
        assertNull(editor.kindOf("MarkerComponent"))
        assertFalse(editor.missingKinds(root, 0L).any { it.name == "MarkerComponent" })
        assertNull(editor.read(root, 0L, "MarkerComponent"))
        val before = root.entity(0)!!.components.toList()
        assertTrue(editor.update(root, 0L, "MarkerComponent", "size", "2") is EditResult.Rejected)
        assertEquals(before, root.entity(0)!!.components.toList())
    }
}
