package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SceneFileEditorTest : BasePlatformTestCase() {
    private val provider = SceneFileEditorProvider()

    private fun file(path: String, text: String = "{}") = myFixture.addFileToProject(path, text).virtualFile

    fun testAcceptsOnlyExactSceneExtension() {
        assertTrue(provider.accept(project, file("a/Main.scene")))
        assertFalse(provider.accept(project, file("a/Main.SCENE")))
        assertFalse(provider.accept(project, file("a/Main.scene.bak")))
        assertFalse(provider.accept(project, file("a/Main.abss")))
        assertFalse(provider.accept(project, myFixture.tempDirFixture.findOrCreateDir("dir.scene")))
    }

    fun testIsPlacedAfterTextEditor() {
        assertEquals(FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR, provider.policy)
    }

    fun testMalformedSceneShowsParseErrorInsteadOfThrowing() {
        for (text in listOf("not json", "[]", "")) {
            val editor = provider.createEditor(project, file("bad/${text.length}.scene", text)) as SceneFileEditor
            try {
                assertNotNull(editor.component)
                val status = editor.statusText
                assertNotNull("status for '$text'", status)
                assertTrue(status!!, status.startsWith("Cannot read scene"))
            } finally {
                editor.dispose()
            }
        }
    }

    fun testValidSceneEditorCreatesAndDisposesWithoutThrowing() {
        // In a headless/GL-less environment the tab shows the glUnavailable message; either way no exception.
        val editor = provider.createEditor(project, file("ok/Main.scene", """{"name":"x"}""")) as SceneFileEditor
        assertNotNull(editor.component)
        editor.dispose()
    }
}
