/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("FieldLeadouts")

package net.nevinsky.abyssus.app.game.controlline.tools

import com.badlogic.gdx.math.Vector3
import java.io.File
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.dto.entityWriter
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.editor.dummy.DummyEntities
import net.nevinsky.abyssus.lib.core.editor.json.JsonObjectWriter
import net.nevinsky.abyssus.lib.core.editor.scene.SceneEdit
import net.nevinsky.abyssus.lib.core.editor.scene.SceneTextEditor
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneParser
import org.slf4j.helpers.NOPLogger

/*
 * The one-off rewrite of Control Line's leadouts (OpenSpec change `add-entity-hierarchy-and-dummies`, task 5.2): for
 * each plane (an entity with a `PlaneComponent`) it adds two `DUMMY` children named `leadoutUp` and `leadoutDown` at
 * the values the plane's `PlaneComponent.leadoutUp` / `leadoutDown` held, then removes those two members from the
 * plane's component. The old values are read from the file (the game's `PlaneComponent` no longer has them), and a
 * scene already migrated is left unchanged, so a rerun changes nothing.
 *
 *   FieldLeadouts <scene file>...   edits each scene file in place
 *
 * GL-free. Run it on a copy first: `./gradlew :app-game-control-line:migrateFieldLeadouts
 * -PfieldLeadoutsScenes=<file>`.
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: FieldLeadouts <scene file>..." }
    val tool = FieldLeadoutsTool()
    for (path in args) {
        val file = File(path)
        val migrated = tool.migrate(file.readText())
        if (migrated == null) {
            println("$path: unchanged")
        } else {
            file.writeText(migrated)
            println("$path: leadouts are dummies")
        }
    }
}

/** The names of a plane's leadout dummies, in the order they are added. */
val LEADOUT_DUMMIES = listOf("leadoutUp", "leadoutDown")

/** The old leadout members of a `PlaneComponent` payload, bound from the file's text. */
class LegacyLeadouts {
    var leadoutUp: Vector3? = null
    var leadoutDown: Vector3? = null
}

/** Rewrites scene text: the scene's planes get leadout dummies, and their components lose the leadout members. */
class FieldLeadoutsTool {
    // no game component is registered: the plane's component stays raw, as the editor holds it
    private val registry = ComponentRegistry()
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER, listOf(entityWriter(registry)))
    private val editor = SceneTextEditor(SceneParser(json, registry), json)

    /** [sceneText] migrated, or null when it holds no leadout to migrate. */
    fun migrate(sceneText: String): String? =
        when (val outcome = editor.edit(sceneText, FieldLeadoutsEdit(json))) {
            is TextEditOutcome.Edited -> outcome.text
            TextEditOutcome.Unchanged -> null
            is TextEditOutcome.Refused -> throw IllegalStateException("the scene cannot be read", outcome.cause)
        }
}

/** The [SceneEdit] behind [FieldLeadoutsTool]. */
class FieldLeadoutsEdit(private val json: JsonProcessor) : SceneEdit {
    private val dummies = DummyEntities(ResourceEditorMessages())
    private val writer = JsonObjectWriter()

    override fun apply(scene: SceneDto): Boolean {
        var changed = false
        for (id in scene.ecs.keys.sorted()) {
            val entity = scene.entity(id) ?: continue
            val wrapper = entity.component(RawComponentsWrapper::class.java) ?: continue
            val at = wrapper.entries.indexOfFirst { it.name.substringAfterLast('.') == "PlaneComponent" }
            if (at < 0) {
                continue
            }
            val raw = wrapper.entries[at]
            val legacy = json.parse(raw.json, LegacyLeadouts::class.java)
            val values = mapOf("leadoutUp" to legacy.leadoutUp, "leadoutDown" to legacy.leadoutDown)
            if (values.values.all { it == null }) {
                continue
            }
            for (name in LEADOUT_DUMMIES) {
                val at3 = values[name] ?: continue
                if (hasDummy(scene, id, name)) {
                    continue
                }
                val added = dummies.add(scene, id).entityId ?: error("entity $id: no dummy was added")
                val dummy = scene.entity(added)!!
                dummy.component(NameComponent::class.java)!!.name = name
                dummy.component(PositionComponent::class.java)!!.localPosition.set(at3)
            }
            wrapper.entries[at] = RawComponent(raw.name, withoutLeadouts(raw.json), raw.reason, raw.disabled)
            changed = true
        }
        return changed
    }

    private fun hasDummy(scene: SceneDto, parentId: Long, name: String): Boolean =
        scene.ecs.values.any {
            it.component(HeaderComponent::class.java)?.type == EntityType.DUMMY &&
                it.component(ParentComponent::class.java)?.parentEntityId?.toLong() == parentId &&
                it.component(NameComponent::class.java)?.name == name
        }

    /** [payload] without the leadout members; the rest are flat values, written back in their order. */
    private fun withoutLeadouts(payload: String): String {
        @Suppress("UNCHECKED_CAST")
        val members = json.parse(payload, LinkedHashMap::class.java) as LinkedHashMap<String, Any?>
        LEADOUT_DUMMIES.forEach(members::remove)
        return String(writer.bytes(members), Charsets.UTF_8)
    }
}
