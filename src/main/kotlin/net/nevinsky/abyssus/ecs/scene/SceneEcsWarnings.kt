/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.ecs.scene

import com.intellij.openapi.diagnostic.Logger

/** Problems met while loading a scene, each message kept and logged once. */
class SceneEcsWarnings {
    private val seen = LinkedHashSet<String>()

    val messages: List<String> get() = seen.toList()

    fun warn(message: String) {
        if (seen.add(message)) LOG.warn(message)
    }

    private companion object {
        val LOG = Logger.getInstance(SceneEcsWarnings::class.java)
    }
}
