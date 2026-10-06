/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.ecs.scene

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.databind.InjectableValues
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.nevinsky.abyssus.runtime.ecs.render.AssetResolver
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsWarnings
import org.slf4j.Logger

@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    creatorVisibility = JsonAutoDetect.Visibility.NONE,
)
private interface PublicFieldsOnly

/**
 * Binds one component entry (the value in an entity's `components`) the way the runtime's scene loader does: libGDX
 * vectors and colors by their public fields, an object that names only some fields merged over the component's own
 * defaults, and decimal text kept. Unlike a scene load it leaves entity references as the file has them.
 */
class ComponentReader(mapper: ObjectMapper, resolver: AssetResolver, log: Logger) {
    private val reader: ObjectReader = mapper.copy()
        .addMixIn(Vector3::class.java, PublicFieldsOnly::class.java)
        .addMixIn(Quaternion::class.java, PublicFieldsOnly::class.java)
        .addMixIn(Color::class.java, PublicFieldsOnly::class.java)
        .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
        .setDefaultMergeable(true)
        .reader(
            InjectableValues.Std()
                .addValue(AssetResolver::class.java.name, resolver)
                .addValue(SceneEcsWarnings::class.java.name, SceneEcsWarnings(log)),
        )

    /** The [type] component [node] holds; throws when it cannot be bound. */
    fun <C : Component> read(type: Class<C>, node: JsonNode): C = reader.forType(type).readValue(node)
}
