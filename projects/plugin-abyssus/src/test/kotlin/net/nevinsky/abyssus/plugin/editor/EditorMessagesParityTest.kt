/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.EditorBundle
import java.util.Properties

/** The plugin and a caller without the IDE read the editing library's messages to the same text. */
class EditorMessagesParityTest : BasePlatformTestCase() {
    fun testEveryEditorMessageReadsTheSameWithAndWithoutTheIde() {
        val keys = Properties().apply {
            EditorMessages::class.java.classLoader.getResourceAsStream("messages/AbyssusEditorBundle.properties")!!.reader().use(::load)
        }.stringPropertyNames()
        val headless = ResourceEditorMessages()
        for (key in keys) {
            assertEquals(key, EditorBundle.message(key), headless.message(key))
            // numbers both as plain arguments (digit grouping included) and as choice arguments
            val params = arrayOf<Any>(1234, 1, 2, 3)
            assertEquals(key, EditorBundle.message(key, *params), headless.message(key, *params))
        }
    }
}
