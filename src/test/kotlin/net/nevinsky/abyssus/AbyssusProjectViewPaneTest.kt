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

package net.nevinsky.abyssus

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.projectView.AbyssusProjectViewPane

class AbyssusProjectViewPaneTest : BasePlatformTestCase() {
    /** ProjectViewImpl.addProjectPane logs a SEVERE error unless the pane's select-in target reports the pane's own id. */
    fun testSelectInTargetMatchesPaneId() {
        val pane = AbyssusProjectViewPane(project)
        try {
            val target = pane.createSelectInTarget()
            assertEquals(pane.id, target.minorViewId)
            assertFalse(target.javaClass.name, target.javaClass.name.endsWith("ProjectPaneSelectInTarget"))
        } finally {
            com.intellij.openapi.util.Disposer.dispose(pane)
        }
    }
}
