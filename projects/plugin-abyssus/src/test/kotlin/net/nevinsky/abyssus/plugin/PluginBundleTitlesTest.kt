/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

class PluginBundleTitlesTest {
    @Test fun everyRegisteredActionAndNotificationHasItsBundleTitle() {
        for (module in listOf("")) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(module + "src/main/resources/META-INF/plugin.xml"))
            val bundle = "AbyssusBundle"
            val properties = Properties().apply { File(module + "src/main/resources/messages/$bundle.properties").reader().use(::load) }
            val actions = document.getElementsByTagName("action")
            for (index in 0 until actions.length) {
                val action = actions.item(index) as org.w3c.dom.Element
                assertFalse(action.hasAttribute("text"))
                assertNotNull(properties.getProperty("action.${action.getAttribute("id")}.text"))
            }
            val groups = document.getElementsByTagName("notificationGroup")
            for (index in 0 until groups.length) {
                val group = groups.item(index) as org.w3c.dom.Element
                assertNotNull(group.getAttribute("id"), properties.getProperty(group.getAttribute("key")))
            }
            if (module.isEmpty()) {
                assertEquals("Rename Scene...", properties.getProperty("action.Abyssus.RenameScene.text"))
                assertEquals("New Terrain...", properties.getProperty("action.Abyssus.NewTerrain.text"))
                assertEquals("Add Light", properties.getProperty("action.Abyssus.AddLight.text"))
                assertEquals("Add Component...", properties.getProperty("action.Abyssus.AddComponent.text"))
                assertEquals("Remove Component", properties.getProperty("action.Abyssus.RemoveComponent.text"))
                assertEquals("Abyssus Properties", properties.getProperty("toolwindow.stripe.Abyssus_Properties"))
            }
        }
    }
}
