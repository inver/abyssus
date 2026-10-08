package net.nevinsky.abyssus.lib.core.editor.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.utils.Json
import net.nevinsky.abyssus.lib.core.io.JsonProcessor


class EcsWriter<T : Engine>(private val jsonProcessor: JsonProcessor) {
    fun write(json: Json, engine: T) {
        prepareEntitySerializer(json, engine)
        json.writeObjectStart()
        writeEntities(json, engine.entities)
        writeSystems(json, engine.getSystems())
        json.writeObjectEnd()
    }


    private fun writeEntities(json: Json, entities: ImmutableArray<Entity?>) {
        json.writeArrayStart("entities")
        for (entity in entities) {
            if (shouldWrite(entity)) {
                json.writeValue(entity)
            }
        }
        json.writeArrayEnd()
    }

    private fun writeSystems(json: Json, systems: ImmutableArray<EntitySystem>) {
        json.writeObjectStart("systems")
        for (system in systems) {
            if (!transientChecker.isTransient(system.javaClass)) {
                writeSystem(json, system)
            }
        }
        json.writeObjectEnd()
    }

    private fun writeSystem(json: Json, system: EntitySystem) {
        val systemType: Class<*> = system.javaClass
        val tag: String? = TagResolver.getTag(json, systemType)
        json.writeObjectStart(tag)
        json.writeValue(system)
        json.writeObjectEnd()
    }
}