/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class PlayExportTest {
    @Test
    fun writesAbsoluteClasspathEntriesAndStableOutput() {
        val project = Files.createTempDirectory("play-export")
        try {
            val relative = "build/libs/game.jar"
            val file = exportPlay(PhysicsOnlyPlayModule::class.java.name, listOf(relative, "", "/opt/lib/gdx.jar"), project)
            assertEquals(project.resolve("abyssus/play.json"), file)
            val text = Files.readString(file)
            val config = PlayFile().parse(text)
            assertEquals(PLAY_PROTOCOL, config.protocol)
            assertEquals(PhysicsOnlyPlayModule::class.java.name, config.module)
            assertEquals(listOf(Path.of(relative).toAbsolutePath().normalize().toString(), Path.of("/opt/lib/gdx.jar").toString()), config.classpath)
            assertTrue(config.classpath.all { Path.of(it).isAbsolute })
            exportPlay(PhysicsOnlyPlayModule::class.java.name, listOf(relative, "", "/opt/lib/gdx.jar"), project)
            assertEquals(text, Files.readString(file))
            assertEquals(
                "{\n  \"protocol\": 1,\n  \"module\": \"net.nevinsky.abyssus.lib.physics.play.PhysicsOnlyPlayModule\",\n  \"classpath\": [\n" +
                    "    ${PlayFile().write(config).lines()[4].trim()}\n    \"/opt/lib/gdx.jar\"\n  ]\n}\n",
                text,
            )
        } finally {
            project.toFile().deleteRecursively()
        }
    }

    @Test
    fun aClassThatIsNotAPlayModuleIsRefused() {
        val project = Files.createTempDirectory("play-export")
        try {
            assertThrows(IllegalArgumentException::class.java) { exportPlay(String::class.java.name, emptyList(), project) }
            assertTrue(Files.notExists(project.resolve(PLAY_FILE)))
        } finally {
            project.toFile().deleteRecursively()
        }
    }

    @Test
    fun parsingRoundTripsJvmArgsAndNamesWhatIsMissing() {
        val config = PlayConfig(1, "M", listOf("/a.jar"), listOf("-Xmx1g"))
        assertEquals(config, PlayFile().parse(PlayFile().write(config)))
        val error = assertThrows(IllegalArgumentException::class.java) { PlayFile().parse("""{"protocol": 1, "classpath": []}""") }
        assertTrue(error.message!!.contains("module"))
    }
}
