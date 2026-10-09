/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import org.junit.Assert.*
import org.junit.Test

/** The current editor models built-ins and leaves game components opaque. */
class SchemaMergeTest {
    @Test fun gameComponentsAreNeverOfferedByTheBuiltInEditor() {
        val editor = ComponentEditor(ResourceEditorMessages())
        val root = SceneJson().parse("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"0":{"components":{"MarkerComponent":{"size":1.000}}}}}}""")
        assertNull(editor.kindOf("MarkerComponent"))
        assertFalse(editor.missingKinds(root, "0").any { it.name == "MarkerComponent" })
        assertNull(editor.read(root, "0", "MarkerComponent"))
        val before = root.toString()
        assertTrue(editor.update(root, "0", "MarkerComponent", "size", "2") is EditResult.Rejected)
        assertEquals(before, root.toString())
    }
}
