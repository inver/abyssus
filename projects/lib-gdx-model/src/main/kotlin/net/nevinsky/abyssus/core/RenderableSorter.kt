/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.utils.Array

/**
 * Responsible for sorting [Renderable] lists by whatever criteria (material, distance to camera, etc.)
 *
 * @author badlogic
 */
interface RenderableSorter {
    /**
     * Sorts the array of [Renderable] instances based on some criteria, e.g. material, distance to camera etc.
     *
     * @param renderables the array of renderables to be sorted
     */
    fun sort(camera: Camera, renderables: Array<Renderable>)
}
