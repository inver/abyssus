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

    private fun ui(vararg targets: String, version: Int = 1): String {
        val buttons = targets.mapIndexed { i, target ->
            val (asset, screen) = target.split(":")
            """{"id":"b$i","type":"button","actions":{"click":{"type":"openScreen","assetName":"$asset","screenId":"$screen"}}}"""
        }.joinToString(",")
        return """{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":$version,"screens":[{"id":"main","root":{"id":"root","type":"panel","children":[$buttons]}}]}}}"""
    }

    private data class UiResult(val unused: Map<String, Boolean>, val issues: Set<UiUsageIssue>)

    private fun uiUsage(registered: String?, metas: Map<String, String>): UiResult {
        val names = registered?.let { ""","uiAssetNames":$it""" } ?: ""
        val abss = myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P"$names}""").virtualFile
        myFixture.addFileToProject("p/scenes/Main.scene", """{"format":"abyssus","formatVersion":1,"ecs":{"1":{"components":{"RenderComponent":{"assetName":"tree"}}}}}""")
        myFixture.addFileToProject("p/assets/tree/meta.json", meta("MODEL", "tree"))
        metas.forEach { (folder, text) -> myFixture.addFileToProject("p/assets/$folder/meta.json", text) }
        val dto = project.service<ProjectReader>().read(abss).obj!!
        return UiResult(dto.assets.associate { it.name to it.unused }, dto.uiUsageIssues.toSet())
    }

    fun testRegisteredMenuAssetsAreUsedAndNavigationReachesOthers() {
        val result = uiUsage("""["menus"]""", mapOf("menus" to ui("settings:main"), "settings" to ui(), "other" to ui()))
        assertFalse(result.unused.getValue("menus"))
        assertFalse(result.unused.getValue("settings"))
        assertTrue(result.unused.getValue("other"))
        assertFalse(result.unused.getValue("tree"))
        assertTrue(result.issues.toString(), result.issues.isEmpty())
    }

    fun testUnregisteredMenuAssetIsUnused() {
        val result = uiUsage(null, mapOf("menus" to ui("settings:main"), "settings" to ui()))
        assertTrue(result.unused.getValue("menus"))
        assertTrue(result.unused.getValue("settings"))
        assertFalse(result.unused.getValue("tree"))
    }

    fun testMissingAndWrongTypeUiReferencesAreDiagnosedWithoutBlockingOthers() {
        val result = uiUsage("""["gone","tree","menus","menus"]""", mapOf("menus" to ui("absent:main", "tree:main")))
        assertFalse(result.unused.getValue("menus"))
        assertFalse(result.unused.getValue("tree"))
        assertEquals(
            setOf(
                UiUsageIssue("gone", UiUsageProblem.MISSING), UiUsageIssue("tree", UiUsageProblem.WRONG_TYPE),
                UiUsageIssue("menus", UiUsageProblem.DUPLICATE), UiUsageIssue("menus", UiUsageProblem.MISSING, "absent"),
                UiUsageIssue("menus", UiUsageProblem.WRONG_TYPE, "tree"),
            ),
            result.issues,
        )
    }

    fun testUnknownScreenInATargetAssetIsDiagnosed() {
        val result = uiUsage("""["menus"]""", mapOf("menus" to ui("settings:nowhere"), "settings" to ui()))
        assertFalse(result.unused.getValue("settings"))
        assertEquals(setOf(UiUsageIssue("menus", UiUsageProblem.MISSING_SCREEN, "settings")), result.issues)
    }

    fun testCyclicUiReferencesTerminateAndAreDiagnosed() {
        val result = uiUsage("""["a"]""", mapOf("a" to ui("b:main"), "b" to ui("a:main", "b:main")))
        assertFalse(result.unused.getValue("a"))
        assertFalse(result.unused.getValue("b"))
        assertEquals(setOf(UiUsageIssue("a", UiUsageProblem.CYCLE), UiUsageIssue("b", UiUsageProblem.CYCLE)), result.issues)
    }

    fun testUnsupportedUiAssetIsNotARootAndItsLinksAreNotFollowed() {
        val result = uiUsage("""["future","menus"]""", mapOf("future" to ui("hidden:main", version = 2), "menus" to ui("future2:main"),
            "hidden" to ui(), "future2" to ui("hidden:main", version = 2)))
        assertTrue(result.unused.getValue("future"))
        assertTrue(result.unused.getValue("hidden"))
        assertFalse(result.unused.getValue("menus"))
        assertFalse(result.unused.getValue("future2"))
        assertEquals(
            setOf(UiUsageIssue("future", UiUsageProblem.UNSUPPORTED), UiUsageIssue("menus", UiUsageProblem.UNSUPPORTED, "future2")),
            result.issues,
        )
    }

    fun testMalformedUiRegistryDoesNotHideSceneUsage() {
        val result = uiUsage("""[1, null, "menus"]""", mapOf("menus" to ui()))
        assertFalse(result.unused.getValue("tree"))
        assertFalse(result.unused.getValue("menus"))
    }
}
