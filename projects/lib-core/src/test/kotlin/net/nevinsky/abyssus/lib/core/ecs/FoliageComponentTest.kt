/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import net.nevinsky.abyssus.lib.core.ecs.component.FoliageComponent
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.testing.loadEcs
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class FoliageComponentTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val registry = ComponentRegistry()

    private fun load(components: String): Pair<SceneEngine, EcsLoadingWarns> {
        val engine = SceneEngine()
        return engine to loadEcs("""{"1":{"components":{$components}}}""", engine, registry, json)
    }

    private fun foliage(engine: SceneEngine) = engine.entities.single().getComponent(FoliageComponent::class.java)

    private fun written(component: FoliageComponent): String = json.componentTree(component).toString()

    @Test
    fun registeredUnderShortAndQualifiedNames() {
        assertSame(FoliageComponent::class.java, registry.get("FoliageComponent"))
        assertSame(FoliageComponent::class.java, registry.get(FoliageComponent::class.qualifiedName!!))
        assertEquals("FoliageComponent", registry.nameOf(FoliageComponent::class.java))
        assertTrue(FoliageComponent::class.java in registry.types)
    }

    @Test
    fun registeringItAgainIsRefused() {
        assertThrows(IllegalStateException::class.java) { registry.register("FoliageComponent", FoliageComponent::class.java) }
        assertThrows(IllegalStateException::class.java) {
            registry.register(FoliageComponent::class.qualifiedName!!, FoliageComponent::class.java)
        }
    }

    @Test
    fun bindsEitherNameAndKeepsOtherMembersLexically() {
        for (key in listOf("FoliageComponent", FoliageComponent::class.qualifiedName!!)) {
            val source = """"$key": {"assetName": "foliage_meadow", "note": "x", "extra": {"weight": 2.50, "epsilon": 1.0E-4, "z": -0.0}}"""
            val (engine, warns) = load(source)
            assertEquals("foliage_meadow", foliage(engine).assetName)
            assertTrue(warns.warnings.toString(), warns.warnings.isEmpty())
            assertEquals(
                """{"assetName":"foliage_meadow","note":"x","extra":{"weight":2.5,"epsilon":1.0E-4,"z":-0.0}}""",
                written(foliage(engine)),
            )
        }
    }

    @Test
    fun missingNameBindsAsNoneAndGainsNoMember() {
        val (engine, warns) = load(""""FoliageComponent": {"note": 1}""")
        assertNull(foliage(engine).assetName)
        assertTrue(warns.warnings.isEmpty())
        assertEquals("""{"note":1}""", written(foliage(engine)))
        val (bare, _) = load(""""FoliageComponent": {}""")
        assertEquals("{}", written(foliage(bare)))
    }

    @Test
    fun nullNameIsLeftOutOnWrite() {
        val (engine, _) = load(""""FoliageComponent": {"assetName": null}""")
        assertNull(foliage(engine).assetName)
        assertEquals("""{}""", written(foliage(engine)))
    }

    @Test
    fun theNameIsWrittenFirstAndOtherMembersKeepTheirOrder() {
        val (engine, _) = load(""""FoliageComponent": {"a": 1, "assetName": "old", "b": 2.0}""")
        val component = foliage(engine)
        component.assetName = "new"
        assertEquals("""{"assetName":"new","a":1,"b":2.0}""", written(component))
        component.assetName = null
        assertEquals("""{"a":1,"b":2.0}""", written(component))
        component.assetName = "old"
        assertEquals("""{"assetName":"old","a":1,"b":2.0}""", written(component))
    }

    @Test
    fun aNewComponentWritesItsNameOnly() {
        assertEquals("{}", written(FoliageComponent()))
        assertEquals("""{"assetName":"grass"}""", written(FoliageComponent().also { it.assetName = "grass" }))
    }

    @Test
    fun aNameThatIsNotAStringStaysRawWithOneWarning() {
        val (engine, warns) = load(""""FoliageComponent": {"assetName": 5.0, "k": 1.50}""")
        val entity = engine.entities.single()
        assertNull(entity.getComponent(FoliageComponent::class.java))
        assertNotNull(entity.getComponent(HeaderComponent::class.java))
        assertEquals(1, warns.warnings.size)
        val raw = warns.carried.getValue(1L).getValue("FoliageComponent")
        assertEquals("""{"assetName":5.0,"k":1.5}""", raw.json)
    }

    @Test
    fun anUnknownFolderNameIsKeptWithoutAnyAssetRead() {
        val (engine, warns) = load(""""FoliageComponent": {"assetName": "does_not_exist"}""")
        assertEquals("does_not_exist", foliage(engine).assetName)
        assertFalse(warns.warnings.any { "does_not_exist" in it })
        assertEquals("""{"assetName":"does_not_exist"}""", written(foliage(engine)))
    }
}
