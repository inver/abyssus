/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerKind
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageError.ALIGNMENT
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageError.DENSITY
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageError.DUPLICATE_ID
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageError.HEIGHT_RANGE
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageError.NOT_A_MODEL
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageError.SCALE_RANGE
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageError.SLOPE
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageError.WEIGHT
import org.junit.Assert.*
import org.junit.Test

class FoliageSettingsTest {
    private val messages = ResourceEditorMessages()
    private val reader = FoliageSettingsReader(messages)
    private val models = setOf("tree")

    /** The `additional` of a foliage meta, read the way the panel reads a `meta.json`. */
    private fun read(additional: String, modelAssets: Set<String> = models): FoliageRead {
        val root = SceneJson().parse("""{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":$additional}""")
        return reader.read(root.get("additional"), modelAssets)
    }

    private fun only(read: FoliageRead, error: FoliageError): FoliageProblem {
        assertEquals(read.problems.map { it.error }.toString(), 1, read.problems.size)
        assertEquals(error, read.problems.single().error)
        return read.problems.single()
    }

    @Test
    fun aValidLayerOfTreeParsesAndHasNoProblems() {
        val read = read(
            """{"terrain":"terr","maskResolution":512,"layers":[{"id":1,"kind":"OBJECT","models":[{"asset":"tree","weight":1.0}]}]}"""
        )
        assertTrue(read.problems.toString(), read.valid)
        assertEquals("terr", read.settings.terrain)
        assertEquals(512, read.settings.maskResolution)
        val layer = read.settings.layers.single()
        assertEquals(1, layer.id)
        assertEquals(FoliageLayerKind.OBJECT, FoliageLayerKind.valueOf(layer.kind))
        assertEquals("tree", layer.models.single().asset)
        assertEquals(1f, layer.models.single().weight, 0f)
        assertEquals(FOLIAGE_DENSITY_DEFAULT, layer.density, 0f)
        assertEquals(FOLIAGE_SCALE_MIN_DEFAULT, layer.scale.min, 0f)
        assertEquals(FOLIAGE_SCALE_MAX_DEFAULT, layer.scale.max, 0f)
        assertEquals(FOLIAGE_ALIGN_DEFAULT, layer.alignToNormal, 0f)
        assertNull(layer.minHeight)
        assertNull(layer.maxHeight)
        assertNull(layer.maxSlope)
        assertEquals(FOLIAGE_DRAW_DISTANCE_DEFAULT, layer.drawDistance, 0f)
        assertEquals(0, layer.seed)
    }

    @Test
    fun aNegativeDensityIsRefused() {
        assertEquals(DENSITY, only(read(layer("""{"id":1,"density":-0.5}""")), DENSITY).error)
    }

    @Test
    fun aScaleMinimumAboveItsMaximumIsRefused() {
        only(read(layer("""{"id":1,"scale":{"min":2.0,"max":1.0}}""")), SCALE_RANGE)
    }

    @Test
    fun anAlignmentAboveOneIsRefused() {
        only(read(layer("""{"id":1,"alignToNormal":2.0}""")), ALIGNMENT)
    }

    @Test
    fun aMaximumSlopeAboveNinetyIsRefused() {
        only(read(layer("""{"id":1,"maxSlope":90.5}""")), SLOPE)
    }

    @Test
    fun aMinimumHeightAboveTheMaximumHeightIsRefused() {
        only(read(layer("""{"id":1,"minHeight":40.0,"maxHeight":5.0}""")), HEIGHT_RANGE)
    }

    @Test
    fun aWeightOfZeroIsRefused() {
        only(read(layer("""{"id":1,"models":[{"asset":"tree","weight":0.0}]}""")), WEIGHT)
    }

    @Test
    fun twoLayersWithTheSameIdAreRefused() {
        val read = read(
            """{"terrain":"terr","layers":[{"id":1,"models":[{"asset":"tree","weight":1.0}]},""" +
                """{"id":1,"models":[{"asset":"tree","weight":1.0}]}]}"""
        )
        assertEquals(FOLIAGE_ID, only(read, DUPLICATE_ID).field)
    }

    @Test
    fun aModelThatIsNotAModelAssetIsRefused() {
        val layer = layer("""{"id":1,"models":[{"asset":"rock","weight":1.0}]}""")
        assertEquals(FOLIAGE_MODELS, only(read(layer), NOT_A_MODEL).field)
        // the same layer is valid once the project has that model asset
        assertTrue(read(layer, models + "rock").valid)
    }

    @Test
    fun eachProblemCarriesTheBundleReason() {
        val read = read(layer("""{"id":1,"density":-1.0,"scale":{"min":2.0,"max":1.0}}"""))
        assertEquals(setOf(DENSITY, SCALE_RANGE), read.problems.map { it.error }.toSet())
        for (problem in read.problems) {
            assertEquals(messages.message("foliageError.${problem.error.name}"), problem.reason)
        }
    }

    @Test
    fun membersOfTheWrongShapeTakeTheirDefaults() {
        val read = read("""{"terrain":"terr","maskResolution":"big","layers":[{"id":"x"},{"id":2,"density":null}]}""")
        assertEquals(FOLIAGE_MASK_RESOLUTION_DEFAULT, read.settings.maskResolution)
        assertEquals(1, read.settings.layers.size)
        assertEquals(2, read.settings.layers[0].id)
        assertEquals(FOLIAGE_DENSITY_DEFAULT, read.settings.layers[0].density, 0f)
        assertTrue(read.problems.toString(), read.valid)
    }

    @Test
    fun aProblemIsFoundByItsLayerAndField() {
        val read = read(layer("""{"id":7,"density":-1.0}"""))
        assertEquals(DENSITY, read.problemOf(7, FOLIAGE_DENSITY)?.error)
        assertNull(read.problemOf(7, FOLIAGE_SEED))
        assertNull(read.problemOf(8, FOLIAGE_DENSITY))
    }

    /** A first layer with [overrides], then a fully stated, valid DETAIL layer. */
    private fun layer(overrides: String): String =
        """{"terrain":"terr","layers":[$overrides,""" +
            """{"id":99,"kind":"DETAIL","models":[{"asset":"tree","weight":3.0}],""" +
            """"density":0.25,"scale":{"min":0.5,"max":1.5},"alignToNormal":1.0,"minHeight":5.0,"maxHeight":40.0,"maxSlope":20.0,""" +
            """"drawDistance":120.0,"seed":-956189611}]}"""
}
