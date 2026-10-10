/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.UUID

class ProjectAssetUsageTest : BasePlatformTestCase() {
    private fun uuid(name: String) = UUID.nameUUIDFromBytes(name.toByteArray()).toString()

    private fun meta(type: String, name: String, additional: String = "{}") =
        """{"format":"abyssus","formatVersion":1,"type":"$type","uuid":"${uuid(name)}","additional":$additional}"""

    private fun foliage() = meta("FOLIAGE", "foliage_meadow", """{"terrain":"terrain","layers":[{"id":1,"models":[{"asset":"tree"},{"asset":"missing"}]},{"id":2,"kind":"DETAIL","models":[{"asset":"grass"}]}]}""")

    private fun usage(withFoliage: Boolean, foliageMeta: String = foliage()): Map<String, Boolean> {
        val abss = myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"}""").virtualFile
        val component = if (withFoliage) """, "FoliageComponent":{"assetName":"foliage_meadow"}""" else ""
        myFixture.addFileToProject("p/scenes/Main.scene", """{"format":"abyssus","formatVersion":1,"ecs":{"1":{"components":{"TerrainComponent":{"assetName":"terrain"}$component}}}}""")
        val metas = mapOf(
            "foliage_meadow" to foliageMeta,
            "terrain" to meta("TERRAIN", "terrain", """{"splatMap":"${uuid("splat")}"}"""),
            "tree" to meta("MODEL", "tree", """{"materials":["${uuid("bark")}"]}"""),
            "grass" to meta("MODEL", "grass"),
            "bark" to meta("MATERIAL", "bark"),
            "splat" to meta("TEXTURE", "splat"),
            "lonely" to meta("MODEL", "lonely"),
        )
        metas.forEach { (folder, text) -> myFixture.addFileToProject("p/assets/$folder/meta.json", text) }
        return project.service<ProjectReader>().read(abss).obj!!.assets.associate { it.name to it.unused }
    }

    fun testFoliageUsedThroughATerrainEntity() {
        assertFalse(usage(true).getValue("foliage_meadow"))
    }

    fun testModelsAndTheirMaterialsUsedOnlyThroughFoliage() {
        val unused = usage(true)
        assertFalse(unused.getValue("tree"))
        assertFalse(unused.getValue("grass"))
        assertFalse(unused.getValue("bark"))
        assertTrue(unused.getValue("lonely"))
    }

    fun testFoliageNothingShows() {
        val unused = usage(false)
        assertTrue(unused.getValue("foliage_meadow"))
        assertTrue(unused.getValue("tree"))
        assertTrue(unused.getValue("grass"))
        assertTrue(unused.getValue("bark"))
    }

    fun testFoliageTerrainAndItsSplatAreUsedWithoutADirectTerrainReference() {
        usage(true)
        val scene = myFixture.findFileInTempDir("p/scenes/Main.scene")
        com.intellij.openapi.application.runWriteAction {
            scene.setBinaryContent("""{"format":"abyssus","formatVersion":1,"ecs":{"1":{"components":{"FoliageComponent":{"assetName":"foliage_meadow"}}}}}""".toByteArray())
        }
        val assets = project.service<ProjectReader>().read(myFixture.findFileInTempDir("p/P.abss")).obj!!.assets
        assertFalse(assets.single { it.name == "terrain" }.unused)
        assertFalse(assets.single { it.name == "splat" }.unused)
    }

    fun testUnsupportedFoliageMetaDoesNotMarkItsDependenciesUsed() {
        val unused = usage(true, foliage().replace("\"formatVersion\":1", "\"formatVersion\":2"))
        assertTrue(unused.getValue("tree"))
        assertTrue(unused.getValue("bark"))
    }
}
