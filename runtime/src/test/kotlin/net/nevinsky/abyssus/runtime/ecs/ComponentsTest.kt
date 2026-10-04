/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.core.ModelInstance
import net.nevinsky.abyssus.core.model.Model
import net.nevinsky.abyssus.runtime.ecs.component.ParentComponent
import net.nevinsky.abyssus.runtime.ecs.component.Point2PointPositionComponent
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.runtime.ecs.render.AssetReference
import net.nevinsky.abyssus.runtime.ecs.render.AssetType
import net.nevinsky.abyssus.runtime.ecs.render.RenderableObjectDelegate
import net.nevinsky.abyssus.runtime.ecs.render.RenderableSceneObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ComponentsTest {
    @Test
    fun defaults() {
        val position = PositionComponent()
        assertEquals(Vector3(), position.localPosition)
        assertEquals(1f, position.localRotation.w, 0f)
        assertEquals(Vector3(1f, 1f, 1f), position.localScale)
        assertEquals(-1, position.lookAtId)
        assertEquals(-1, ParentComponent().parentEntityId)
        assertEquals(-1, Point2PointPositionComponent().entity1Id)
        assertEquals(-1, Point2PointPositionComponent().entity2Id)
    }

    @Test
    fun transformAndTranslate() {
        val position = PositionComponent(1f, 2f, 3f)
        position.translate(1f, 1f, 1f)
        position.translate(Vector3(1f, 0f, 0f))
        assertEquals(Vector3(3f, 3f, 4f), position.getPosition(Vector3()))
        assertEquals(Vector3(3f, 3f, 4f), position.getLocalPosition(Vector3()))
        assertArrayEquals(Matrix4().setToTranslation(3f, 3f, 4f).values, position.getTransform().values, 0f)
    }

    @Test
    fun delegateSetsTransformAndWrapsAsComponent() {
        val instance = ModelInstance(Model())
        val asset = object : RenderableSceneObject by AssetReference("m", AssetType.MODEL) {
            override val modelInstance = instance
        }
        val delegate = RenderableObjectDelegate(asset, "defaultShader")
        delegate.setPosition(Matrix4().setToTranslation(1f, 2f, 3f))
        assertEquals(Vector3(1f, 2f, 3f), instance.transform!!.getTranslation(Vector3()))
        assertSame(delegate, delegate.asComponent().renderable)
        assertNull(delegate.asComponent().raw)
    }

    @Test
    fun referenceHasNoGeometry() {
        assertNull(AssetReference("m", AssetType.TERRAIN).modelInstance)
    }
}
