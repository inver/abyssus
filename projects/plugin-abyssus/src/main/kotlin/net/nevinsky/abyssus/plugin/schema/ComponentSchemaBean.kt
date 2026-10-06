/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.schema

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.xmlb.annotations.Attribute

/**
 * A component schema another plugin contributes through `net.nevinsky.abyssus.componentSchemas`: [resource] is a path
 * in that plugin's jar to a file in the format of `abyssus/components.schema.json`. Its components are offered in every
 * project, unless the project's own schema declares the same short name.
 */
class ComponentSchemaBean : PluginAware {
    @Attribute("resource")
    var resource: String = ""

    var plugin: PluginDescriptor? = null
        private set

    override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
        plugin = pluginDescriptor
    }

    /** The contributing plugin's name, for messages. */
    val pluginName: String get() = plugin?.name ?: plugin?.pluginId?.idString ?: "?"

    /** The schema text, or null when the plugin's jar has no such resource. */
    fun text(): String? {
        val loader = plugin?.classLoader ?: ComponentSchemaBean::class.java.classLoader
        return loader.getResourceAsStream(resource.removePrefix("/"))?.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}

/** The extension point other plugins contribute component schemas to. */
val COMPONENT_SCHEMAS_EP: ExtensionPointName<ComponentSchemaBean> = ExtensionPointName.create("net.nevinsky.abyssus.componentSchemas")
