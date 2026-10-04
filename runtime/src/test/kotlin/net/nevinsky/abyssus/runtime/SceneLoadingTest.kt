/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.runtime

import net.nevinsky.abyssus.testing.warningsTo
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.runtime.ecs.component.NameComponent
import net.nevinsky.abyssus.runtime.ecs.component.TypeComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderableObjectDelegate
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class SceneLoadingTest {
    private val json = JsonProcessor()
    private fun loading(messages: MutableList<String> = mutableListOf()) =
        SceneLoading(json, warningsTo(messages))
    private val folder get() = testProject("Untitled").toPath()
    private val main get() = folder.resolve("scenes/Main Scene.scene")

    @Test fun nativeLoadingRejectsLegacyAndFutureDocumentsWithoutBlockingSiblings() {
        val messages = mutableListOf<String>()
        val service = loading(messages)
        for (text in listOf("{}", """{"format":"abyssus","formatVersion":2}""", """{"format":"abyssus","formatVersion":1,"ecs":{"componentIdentifiers":{}}}""")) {
            assertNull(service.load(text, folder))
            if (!text.contains("componentIdentifiers")) assertThrows(net.nevinsky.abyssus.assets.format.UnsupportedDocumentFormat::class.java) { service.projectName(text) }
        }
        assertEquals(3, messages.size)
        assertNotNull(service.load(main))
    }

    @Test fun untitledProjectReadsOutsideTheIde() {
        val project = requireNotNull(loading().project(folder))
        assertEquals("Untitled", project.name)
        assertEquals(listOf("Main Scene.scene"), project.scenes.map { it.fileName.toString() })
    }

    @Test fun aBrokenProjectNamesTheAbssInTheLog() {
        val temp = Files.createTempDirectory("runtime-broken-project")
        try {
            Files.writeString(temp.resolve("Broken.abss"), "not JSON")
            val messages = mutableListOf<String>()
            assertNull(loading(messages).project(temp))
            assertEquals(1, messages.size)
            assertTrue(messages.single().contains("Broken.abss"))
        } finally { temp.toFile().deleteRecursively() }
    }

    @Test fun projectNameKeepsTheExistingDtoBinding() {
        assertEquals("123", loading().projectName("""{"format":"abyssus","formatVersion":1,"name":123}"""))
        assertNull(loading().projectName("""{"format":"abyssus","formatVersion":1}"""))
    }

    @Test fun anEditorParseFailureIsLoggedAndRethrown() {
        val messages = mutableListOf<String>()
        assertThrows(Exception::class.java) { loading(messages).parse("not JSON", "Broken.scene") }
        assertEquals(1, messages.size)
        assertTrue(messages.single().contains("Broken.scene"))
    }

    @Test fun anEditorReadFailureIsLoggedAndRethrown() {
        val messages = mutableListOf<String>()
        val error = java.io.IOException("unreadable VFS file")
        val thrown = assertThrows(java.io.IOException::class.java) {
            loading(messages).parse("Unreadable.scene") { throw error }
        }
        assertSame(error, thrown)
        assertEquals(1, messages.size)
        assertTrue(messages.single().contains("Unreadable.scene"))
    }

    @Test fun anEditorProjectFailureIsLoggedAndRethrown() {
        val messages = mutableListOf<String>()
        assertThrows(Exception::class.java) { loading(messages).projectName("Broken.abss") { "not JSON" } }
        assertEquals(1, messages.size)
        assertTrue(messages.single().contains("Broken.abss"))
    }

    @Test fun mainScenesEntitiesLoadOutsideTheIde() {
        val loaded = requireNotNull(loading().load(main))
        assertEquals("Ololo", loaded.scene.name)
        assertEquals("skybox_physical", loaded.scene.skyboxName)
        assertEquals((0..8).toSet(), loaded.engine.ids.ids)
        val model = loaded.engine.ids[0]!!
        assertEquals("Model 0", model.getComponent(NameComponent::class.java).name)
        assertEquals(TypeComponent.Type.OBJECT, model.getComponent(TypeComponent::class.java).type)
        assertEquals("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb",
            (model.getComponent(RenderComponent::class.java).renderable as RenderableObjectDelegate).asset.assetName)
        val spot = loaded.engine.ids[8]!!
        assertEquals("Spot Light 8", spot.getComponent(NameComponent::class.java).name)
        assertEquals(TypeComponent.Type.LIGHT_SPOT, spot.getComponent(TypeComponent::class.java).type)
    }

    @Test fun customProjectLoadsWithoutItsGame() {
        val messages = mutableListOf<String>()
        val loaded = requireNotNull(loading(messages).load(testProject("Custom").toPath().resolve("scenes/Field.scene")))
        assertEquals(setOf(0, 1), loaded.engine.ids.ids)
        val raw = loaded.engine.ids[0]!!.getComponent(net.nevinsky.abyssus.runtime.ecs.component.RawComponentsComponent::class.java)
        assertEquals("""{"lineLength":22,"kind":"STUNT"}""", raw.components["PlaneComponent"].toString())
        assertEquals(1, messages.count { "PlaneComponent" in it })
        assertEquals(1, messages.size)
    }

    @Test fun unsavedTextLoadsWithoutReadingTheFile() {
        val before = Files.readAllBytes(main)
        val text = Files.readString(main).replace("Model 0", "Plane")
        val missingFolder = Files.createTempDirectory("runtime-unsaved")
        try {
            val loaded = requireNotNull(loading().load(text, missingFolder))
            assertEquals("Plane", loaded.engine.ids[0]!!.getComponent(NameComponent::class.java).name)
            assertEquals((0..8).toSet(), loaded.engine.ids.ids)
            assertArrayEquals(before, Files.readAllBytes(main))
            assertFalse(Files.exists(missingFolder.resolve("scenes/Main Scene.scene")))
        } finally { Files.delete(missingFolder) }
    }

    @Test fun anUnreadableSceneIsLoggedOnceAndOthersLoad() {
        val temp = Files.createTempDirectory("runtime-scenes")
        try {
            Files.copy(folder.resolve("Untitled.abss"), temp.resolve("Untitled.abss"))
            Files.createDirectory(temp.resolve("scenes"))
            Files.copy(main, temp.resolve("scenes/Main Scene.scene"))
            val bad = temp.resolve("scenes/Broken.scene")
            Files.writeString(bad, "not JSON")
            val messages = mutableListOf<String>()
            val service = loading(messages)
            val scenes = requireNotNull(service.project(temp)).scenes.mapNotNull(service::load)
            assertEquals(1, scenes.size)
            assertEquals("Ololo", scenes.single().scene.name)
            assertEquals(1, messages.count { "Broken.scene" in it })
        } finally { temp.toFile().deleteRecursively() }
    }

    @Test fun twoLoadsShareNothing() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val logs = listOf(mutableListOf<String>(), mutableListOf<String>())
            val services = logs.map(::loading)
            val paths = listOf(main, testProject("Animated").toPath().resolve("scenes/Main.scene"))
            val loaded = pool.invokeAll(paths.mapIndexed { i, path -> Callable { requireNotNull(services[i].load(path)) } }).map { it.get() }
            assertEquals((0..8).toSet(), loaded[0].engine.ids.ids)
            assertEquals(setOf(1, 2), loaded[1].engine.ids.ids)
            assertNotSame(loaded[0].engine, loaded[1].engine)
            assertNotSame(loaded[0].engine.ids[1], loaded[1].engine.ids[1])
            loaded[0].engine.ids[0]!!.getComponent(NameComponent::class.java).name = "Changed"
            assertNotEquals("Changed", loaded[1].engine.ids[0]?.getComponent(NameComponent::class.java)?.name)
            assertTrue(logs[0].any { "PickableComponent" in it })
            assertFalse(logs[1].any { "PickableComponent" in it })
        } finally { pool.shutdownNow() }
    }
}
