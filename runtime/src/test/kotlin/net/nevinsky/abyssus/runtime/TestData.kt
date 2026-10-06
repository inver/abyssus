/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.runtime

import net.nevinsky.abyssus.runtime.json.number

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

/** Shared native test fixtures, supplied by Gradle so tests do not depend on their working directory. */
fun testProject(name: String): File =
    File(
        checkNotNull(System.getProperty("abyssus.testData")) { "run through Gradle: abyssus.testData is not set" },
        "project/$name"
    )

/** Keeps decimal spellings (`7.000`) in test inputs, as the plugin's number-preserving JSON nodes do. */
fun testJson(text: String): com.fasterxml.jackson.databind.JsonNode =
    com.fasterxml.jackson.databind.json.JsonMapper.builder()
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .nodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true))
        .build().readTree(text)

/** The component of class [type] that [net.nevinsky.abyssus.runtime.ecs.EcsLoader] makes of [node] (one entity, id 0). */
fun <C : com.badlogic.ashley.core.Component> loadComponent(
    type: Class<C>,
    node: com.fasterxml.jackson.databind.JsonNode,
    resolver: net.nevinsky.abyssus.runtime.ecs.render.AssetResolver =
        net.nevinsky.abyssus.runtime.ecs.render.AssetResolver { t, n -> net.nevinsky.abyssus.runtime.ecs.render.AssetReference(n, t) },
    game: net.nevinsky.abyssus.runtime.schema.GameComponents = net.nevinsky.abyssus.runtime.schema.GameComponents(),
): C {
    val entities = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
    entities.putObject("0").putObject("components").set<com.fasterxml.jackson.databind.JsonNode>(type.simpleName, node)
    // other entities, so a reference in [node] (a look-at target, a parent) is to an entity that exists
    (1..9).forEach { entities.putObject(it.toString()).putObject("components") }
    val ecs = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().set<com.fasterxml.jackson.databind.JsonNode>("entities", entities)
    val scene = testConfigurator(resolver, game = game).load(ecs)
    return checkNotNull(scene.engine.ids[0]!!.getComponent(type)) { "${type.simpleName} was not bound from $node" }
}

/** The scene [EcsConfigurator] over the shared JSON mapper; [log] receives the loader's warnings. */
fun testConfigurator(
    resolver: net.nevinsky.abyssus.runtime.ecs.render.AssetResolver = net.nevinsky.abyssus.runtime.ecs.render.AssetResolver { _, _ -> null },
    log: org.slf4j.Logger = org.slf4j.helpers.NOPLogger.NOP_LOGGER,
    game: net.nevinsky.abyssus.runtime.schema.GameComponents = net.nevinsky.abyssus.runtime.schema.GameComponents(),
) = net.nevinsky.abyssus.runtime.ecs.EcsConfigurator(net.nevinsky.abyssus.core.JsonProcessor().mapper, resolver, log, game)

/** [component] as the scene file holds it, written by [net.nevinsky.abyssus.runtime.ecs.EcsWriter]. */
fun writeComponent(
    component: com.badlogic.ashley.core.Component,
    game: net.nevinsky.abyssus.runtime.schema.GameComponents = net.nevinsky.abyssus.runtime.schema.GameComponents(),
): com.fasterxml.jackson.databind.JsonNode =
    net.nevinsky.abyssus.runtime.ecs.EcsWriter(net.nevinsky.abyssus.core.JsonProcessor().mapper, game).writeComponent(component)
