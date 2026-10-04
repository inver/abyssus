/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.runtime

import java.io.File

/** Shared Mundus fixtures, supplied by Gradle so tests do not depend on their working directory. */
fun testProject(name: String): File =
    File(checkNotNull(System.getProperty("abyssus.testData")) { "run through Gradle: abyssus.testData is not set" }, "project/$name")

/** Keeps decimal spellings in codec test inputs while using only the shared JVM JSON configuration. */
fun testJson(text: String): com.fasterxml.jackson.databind.JsonNode =
    net.nevinsky.abyssus.assets.json.JsonFormat().mapperBuilder()
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .nodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true))
        .build().readTree(text)
