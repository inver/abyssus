/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.runtime.schema

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3

/**
 * A test game component with one field of each type. Its export is `src/test/testData/project/Custom/abyssus/
 * components.schema.json`, byte for byte (`SchemaFileTest`); change both together.
 */
@SceneComponent("PlaneComponent", label = "Plane")
class PlaneComponent : Component {
    enum class Kind { TRAINER, STUNT, SPEED }

    @Field(label = "Line length", group = "Lines", min = 5.0, max = 30.0)
    var lineLength = 18f

    @Field(label = "Fuel seconds", min = 0.0)
    var fuelSeconds = 60

    @Field(label = "Tip weight")
    var hasTipWeight = true

    @Field(label = "Name")
    var name = ""

    @Field(label = "Kind")
    var kind = Kind.TRAINER

    @Field(label = "Leadout", group = "Lines")
    var leadout = Vector3(0f, 0f, -0.3f)

    @Field(label = "Paint")
    var paint = Color(1f, 1f, 1f, 1f)

    @Field(label = "Pilot")
    @EntityRef
    var pilot = -1

    @Field(label = "Model")
    @AssetRef("MODEL")
    var model = ""

    /** Not a `@Field`: not in the schema and never written. */
    var speed = 0f
}

/** The registry a game passes to the scene loader and the schema export. */
class PlaneRegistry : ComponentRegistry {
    override fun components() = listOf(PlaneComponent::class.java)
}
