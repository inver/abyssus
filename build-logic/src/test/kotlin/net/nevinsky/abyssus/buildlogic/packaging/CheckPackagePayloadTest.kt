/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.buildlogic.packaging

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CheckPackagePayloadTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun jar(dir: File, name: String, entry: String = "a/B.class") {
        dir.mkdirs()
        ZipOutputStream(File(dir, name).outputStream()).use { it.putNextEntry(ZipEntry(entry)); it.write(1); it.closeEntry() }
    }

    private class Layout(val image: File, val dist: File, val launcherClasspath: File)

    /** An app-image with game/ (equal to the distribution's lib/), launcher/ and installer.digest. */
    private fun layout(): Layout {
        val image = tmp.newFolder()
        val dist = tmp.newFolder()
        val launcherCp = tmp.newFolder()
        for (name in listOf("app-game-control-line.jar", "gdx.jar")) {
            jar(File(image, "app/game"), name); jar(dist, name)
        }
        for (name in listOf("app-game-control-line-launcher.jar", "lib-updater.jar")) {
            jar(File(image, "app/launcher"), name); jar(launcherCp, name)
        }
        File(image, "app/installer.digest").writeText("abc\n")
        return Layout(image, dist, launcherCp)
    }

    private fun run(l: Layout) {
        val project = ProjectBuilder.builder().withProjectDir(tmp.newFolder()).build()
        val task = project.tasks.register("check", CheckPackagePayload::class.java).get()
        task.appImage.set(l.image)
        task.distributionLib.set(l.dist)
        task.launcherJars.from(l.launcherClasspath.listFiles()!!.toList())
        task.check()
    }

    private fun refused(l: Layout, vararg fragments: String) {
        try {
            run(l)
            fail("expected the check to fail")
        } catch (e: GradleException) {
            fragments.forEach { assertTrue("'${e.message}' lacks '$it'", e.message!!.contains(it)) }
        }
    }

    @Test
    fun `clean payload passes`() = run(layout())

    @Test
    fun `an editor jar in launcher or game fails naming it`() {
        val l = layout()
        jar(File(l.image, "app/launcher"), "lib-core-editor-1.jar")
        refused(l, "launcher/lib-core-editor-1.jar")
        val g = layout()
        jar(File(g.image, "app/game"), "lib-raytracing.jar"); jar(g.dist, "lib-raytracing.jar")
        refused(g, "game/lib-raytracing.jar")
    }

    @Test
    fun `editor code inside a jar fails`() {
        val l = layout()
        jar(File(l.image, "app/game"), "gdx.jar", "net/nevinsky/abyssus/lib/gamelink/Play.class")
        refused(l, "game/gdx.jar", "gamelink")
    }

    @Test
    fun `a missing game jar fails`() {
        val l = layout()
        File(l.image, "app/game/gdx.jar").delete()
        refused(l, "game/gdx.jar", "not in the package")
    }

    @Test
    fun `a jar outside the launcher's classpath fails`() {
        val l = layout()
        jar(File(l.image, "app/launcher"), "extra.jar")
        refused(l, "launcher/extra.jar")
    }

    @Test
    fun `a missing digest fails`() {
        val l = layout()
        File(l.image, "app/installer.digest").delete()
        refused(l, "installer.digest")
    }
}
