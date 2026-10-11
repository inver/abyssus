/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.schema

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson

/** The `componentSchemas` extension point: a contribution is offered while its plugin is loaded, and only then. */
class ComponentSchemaContributionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun contribute(parent: Disposable) {
        val bean = ComponentSchemaBean().apply { resource = "/schemas/markers.json" }
        COMPONENT_SCHEMAS_EP.point.registerExtension(bean, parent)
    }

    fun testContributionsDoNotExtendTheBuiltInEditor() {
        val scene = myFixture.copyFileToProject("Untitled/scenes/Main Scene.scene", "Untitled/scenes/Main Scene.scene")
        myFixture.copyFileToProject("Untitled/Untitled.abss", "Untitled/Untitled.abss")
        val schemas = ComponentSchemas.of(project)
        val root = SceneJson().parse(String(scene.contentsToByteArray()))
        assertNull(schemas.editorFor(scene).kindOf("MarkerComponent"))

        val plugin = Disposer.newDisposable()
        try {
            contribute(plugin)
            assertFalse("MarkerComponent" in schemas.editorFor(scene).missingKinds(root, "0").map { it.name })
            assertNull(schemas.editorFor(scene).kindOf("MarkerComponent"))
        } finally {
            Disposer.dispose(plugin)
        }
        assertNull(schemas.editorFor(scene).kindOf("MarkerComponent"))
    }

    fun testProjectSchemaDoesNotExtendTheBuiltInEditor() {
        val scene = myFixture.copyFileToProject("Custom/scenes/Field.scene", "Custom/scenes/Field.scene")
        myFixture.copyFileToProject("Custom/Custom.abss", "Custom/Custom.abss")
        myFixture.copyFileToProject("Custom/abyssus/components.schema.json", "Custom/abyssus/components.schema.json")
        val schemas = ComponentSchemas.of(project)
        assertNull(schemas.editorFor(scene).kindOf("PlaneComponent"))
    }
}
