/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.runtime

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.runtime.ecs.component.NameComponent
import net.nevinsky.abyssus.runtime.ecs.component.TypeComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderableObjectDelegate
import net.nevinsky.abyssus.core.project.Project
import net.nevinsky.abyssus.runtime.schema.ComponentRegistrationException
import net.nevinsky.abyssus.runtime.schema.ComponentRegistry
import net.nevinsky.abyssus.runtime.schema.PlaneComponent
import net.nevinsky.abyssus.runtime.schema.PlaneRegistry
import net.nevinsky.abyssus.runtime.schema.SceneComponent
import net.nevinsky.abyssus.testing.warningsTo
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class RuntimeSceneLoaderTest {
    private val json = JsonProcessor()

    private fun loader(
        project: File = testProject("Tree"),
        messages: MutableList<String> = mutableListOf(),
        registry: ComponentRegistry = ComponentRegistry { emptyList() },
    ) = RuntimeSceneLoader(json, FileLoader(project), warningsTo(messages), registry)

    private val main = "Main Scene.scene"

    @Test
    fun mainScenesEntitiesLoadOutsideTheIde() {
        val loaded = requireNotNull(loader().load(main))
        assertEquals("Ololo", loaded.scene.name)
        assertEquals("skybox_physical", loaded.scene.skyboxName)
        assertEquals((0..8).toSet(), loaded.engine.ids.ids)
        val model = loaded.engine.ids[0]!!
        assertEquals("Model 0", model.getComponent(NameComponent::class.java).name)
        assertEquals(TypeComponent.Type.OBJECT, model.getComponent(TypeComponent::class.java).type)
        assertEquals(
            "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb",
            (model.getComponent(RenderComponent::class.java).renderable as RenderableObjectDelegate).asset.assetName,
        )
        val spot = loaded.engine.ids[8]!!
        assertEquals("Spot Light 8", spot.getComponent(NameComponent::class.java).name)
        assertEquals(TypeComponent.Type.LIGHT_SPOT, spot.getComponent(TypeComponent::class.java).type)
    }

    @Test
    fun whatTheSceneHoldsThatIsNotModelledIsLoggedOnceUnderTheScenesName() {
        val messages = mutableListOf<String>()
        val loaded = requireNotNull(loader(messages = messages).load(main))
        assertEquals(1, messages.count { "PickableComponent" in it })
        assertTrue(messages.toString(), messages.all { it.startsWith("$main: ") })
        assertEquals(loaded.document.warnings.map { "$main: $it" }, messages)
    }

    @Test
    fun customProjectLoadsWithoutItsGameAndWithItWhenRegistered() {
        val messages = mutableListOf<String>()
        val without = requireNotNull(loader(testProject("Custom"), messages).load("Field.scene"))
        assertEquals(setOf(0, 1), without.engine.ids.ids)
        assertEquals("""{"lineLength":22,"kind":"STUNT"}""", without.document.carried[0L]!!["PlaneComponent"].toString())
        assertEquals(1, messages.count { "PlaneComponent" in it })
        assertEquals(1, messages.size)

        val with = requireNotNull(loader(testProject("Custom"), registry = PlaneRegistry()).load("Field.scene"))
        val plane = with.engine.ids[0]!!.getComponent(PlaneComponent::class.java)
        assertEquals(22f, plane.lineLength, 0f)
        assertEquals(PlaneComponent.Kind.STUNT, plane.kind)
        assertEquals(emptyMap<Long, Any>(), with.document.carried)
    }

    @Test
    fun unsavedTextLoadsWithoutReadingTheFile() {
        val before = Files.readAllBytes(testProject("Tree").toPath().resolve("scenes/$main"))
        val text = String(before).replace("Model 0", "Plane")
        val missingFolder = Files.createTempDirectory("runtime-unsaved")
        try {
            val loaded = requireNotNull(loader(missingFolder.toFile()).loadFromText(text))
            assertEquals("Plane", loaded.engine.ids[0]!!.getComponent(NameComponent::class.java).name)
            assertEquals((0..8).toSet(), loaded.engine.ids.ids)
            assertArrayEquals(before, Files.readAllBytes(testProject("Tree").toPath().resolve("scenes/$main")))
            assertFalse(Files.exists(missingFolder.resolve("scenes/$main")))
        } finally {
            Files.delete(missingFolder)
        }
    }

    @Test
    fun aSceneThatCannotBeReadIsLoggedOnceWithItsNameAndLoadsAsNull() {
        val messages = mutableListOf<String>()
        val loader = loader(messages = messages)
        assertNull(loader.load("Nope.scene"))
        assertNull(loader.loadFromText("not JSON"))
        assertEquals(2, messages.size)
        assertTrue(messages[0].contains("Nope.scene"))
        assertTrue(messages[1].contains("the scene text"))
    }

    @Test
    fun anUnreadableSceneIsLoggedOnceAndOthersLoad() {
        val temp = Files.createTempDirectory("runtime-scenes")
        try {
            Files.createDirectory(temp.resolve("scenes"))
            Files.copy(testProject("Tree").toPath().resolve("scenes/$main"), temp.resolve("scenes/$main"))
            Files.writeString(temp.resolve("scenes/Broken.scene"), "not JSON")
            val messages = mutableListOf<String>()
            val loader = loader(temp.toFile(), messages)
            val names = Project(dir = temp).sceneFiles().map { it.fileName.toString() }
            assertEquals(listOf("Broken.scene", main), names)
            val scenes = names.mapNotNull(loader::load)
            assertEquals(1, scenes.size)
            assertEquals("Ololo", scenes.single().scene.name)
            assertEquals(1, messages.count { "Broken.scene" in it && it.startsWith("Could not load") })
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun twoLoadsShareNothing() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val logs = listOf(mutableListOf<String>(), mutableListOf<String>())
            val loaders = listOf(loader(testProject("Tree"), logs[0]), loader(testProject("Animated"), logs[1]))
            val names = listOf(main, "Main.scene")
            val loaded = pool.invokeAll(names.mapIndexed { i, name -> Callable { requireNotNull(loaders[i].load(name)) } })
                .map { it.get() }
            assertEquals((0..8).toSet(), loaded[0].engine.ids.ids)
            assertEquals(setOf(1, 2), loaded[1].engine.ids.ids)
            assertNotSame(loaded[0].engine, loaded[1].engine)
            assertNotSame(loaded[0].engine.ids[1], loaded[1].engine.ids[1])
            loaded[0].engine.ids[0]!!.getComponent(NameComponent::class.java).name = "Changed"
            assertNotEquals("Changed", loaded[1].engine.ids[0]?.getComponent(NameComponent::class.java)?.name)
            assertTrue(logs[0].any { "PickableComponent" in it })
            assertFalse(logs[1].any { "PickableComponent" in it })
        } finally {
            pool.shutdownNow()
        }
    }

    @SceneComponent("NameComponent")
    class FakeName : Component

    @Test
    fun aRegistrationThatFailsFailsTheConstructionSoNoSceneLoads() {
        val error = assertThrows(ComponentRegistrationException::class.java) {
            loader(registry = ComponentRegistry { listOf(FakeName::class.java) })
        }
        assertTrue(error.message, error.message!!.contains("NameComponent is a built-in component"))
    }
}
