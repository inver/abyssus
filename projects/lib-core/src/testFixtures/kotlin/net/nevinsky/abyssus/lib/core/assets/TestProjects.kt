package net.nevinsky.abyssus.lib.core.assets

import java.io.File

/** A fixture project under the repository's `projects/plugin-abyssus/src/test/testData/project`, shared by every module's tests. */
fun testProject(name: String): File =
    File(checkNotNull(System.getProperty("abyssus.testData")) { "run through Gradle: abyssus.testData is not set" }, "project/$name")
