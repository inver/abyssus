/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelmesh

import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.assets.model.PartHinge
import net.nevinsky.abyssus.lib.core.assets.model.PartPivot
import net.nevinsky.abyssus.lib.core.assets.model.PartVec3
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class ModelPartEditsTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val parser = AssetMetaParser(json)
    private val edits = ModelPartEdits(AssetMetaTextEditor(parser), ResourceEditorMessages())

    private val original = """{
  "format" : "abyssus",
  "formatVersion" : 1,
  "version" : 1,
  "lastModified" : 1,
  "type" : "MODEL",
  "additional" : {
    "file" : "model.glb",
    "format" : "GLTF",
    "binary" : true,
    "materials" : [ ]
  }
}
"""

    /** The text after one no-op edit, so it is in the printer's own spelling. */
    private val printed: String =
        (edits.setHinge(original, "X", PartHinge(-1f, 1f)) as TextEditOutcome.Edited).text.let {
            (edits.clearHinge(it, "X") as TextEditOutcome.Edited).text
        }

    private fun edited(outcome: TextEditOutcome) = (outcome as TextEditOutcome.Edited).text

    @Test
    fun writingThenClearingAPivotRestoresTheText() {
        val with = edited(edits.setPivot(printed, "LeftFlap", PartPivot(PartVec3(0f, -0.002f, 0f))))
        assertTrue(with, with.contains("LeftFlap"))
        assertEquals(printed, edited(edits.clearPivot(with, "LeftFlap")))
        assertFalse(printed.contains("\"parts\""))
    }

    @Test
    fun aHingeWithMinAboveZeroIsRefused() {
        val outcome = edits.setHinge(printed, "LeftFlap", PartHinge(10f, 35f))
        assertTrue(outcome is TextEditOutcome.Refused)
        assertEquals("The minimum must be 0 or less.", (outcome as TextEditOutcome.Refused).cause.message)
    }

    @Test
    fun anEntryWithNeitherMemberIsRemoved() {
        val both = edited(edits.setHinge(edited(edits.setPivot(printed, "A", PartPivot())), "A", PartHinge(-5f, 5f)))
        val noPivot = edited(edits.clearPivot(both, "A"))
        assertTrue(noPivot.contains("hinge"))
        val none = edited(edits.clearHinge(noPivot, "A"))
        assertFalse(none.contains("\"A\""))
        assertFalse(none.contains("parts"))
        assertEquals(TextEditOutcome.Unchanged, edits.clearHinge(none, "A"))
    }

    @Test
    fun formatVersionStaysOne() {
        val text = edited(edits.setHinge(printed, "LeftFlap", PartHinge(-35f, 35f)))
        assertTrue(text, Regex("\"formatVersion\"\\s*:\\s*1\\b").containsMatchIn(text))
        assertEquals(-35f, parser.read("", text).typedAdditional<net.nevinsky.abyssus.lib.core.assets.model.ModelMeta>().parts!!.getValue("LeftFlap").hinge!!.min, 0f)
    }

    @Test
    fun limitsRules() {
        val limits = HingeLimits()
        assertNull(limits.problem(-35f, 35f))
        assertEquals(HingeProblem.MIN_ABOVE_ZERO, limits.problem(1f, 2f))
        assertEquals(HingeProblem.MAX_BELOW_ZERO, limits.problem(-2f, -1f))
        assertEquals(HingeProblem.OUT_OF_RANGE, limits.problem(-181f, 0f))
        assertEquals(HingeProblem.NOT_FINITE, limits.problem(Float.NaN, 0f))
    }
}
