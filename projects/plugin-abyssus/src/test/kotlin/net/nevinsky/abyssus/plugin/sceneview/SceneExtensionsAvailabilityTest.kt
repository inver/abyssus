package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.filetype.AbyssusProjectSettings
import net.nevinsky.abyssus.plugin.physics.PhysicsOverlayProvider
import net.nevinsky.abyssus.plugin.physics.PhysicsSimulationProvider

class SceneExtensionsAvailabilityTest : BasePlatformTestCase() {
    fun testProvidersCompiledAgainstTheOldInterfacesInheritAvailability() {
        val dir = java.nio.file.Files.createTempDirectory("abyssus-old-provider").toFile()
        try {
            val pkg = "net.nevinsky.abyssus.plugin.sceneview"
            val sources = listOf(
                "SceneSimulationProvider.java" to """package $pkg; public interface SceneSimulationProvider {
                    SceneSimulation start(SimulationRequest r, SimulationListener l); }""",
                "SceneOverlayProvider.java" to """package $pkg; public interface SceneOverlayProvider {
                    SceneOverlay create(com.intellij.openapi.project.Project p, com.intellij.openapi.vfs.VirtualFile f); }""",
                "OldSimulation.java" to """package legacy; public class OldSimulation implements $pkg.SceneSimulationProvider {
                    public $pkg.SceneSimulation start($pkg.SimulationRequest r, $pkg.SimulationListener l) {
                        return new $pkg.SceneSimulation() {
                            public void pause() {} public void resume() {} public void step() {} public void stop() {}
                            public void input($pkg.SimulationInput e) {}
                            public java.util.Map<String, net.nevinsky.abyssus.lib.core.editor.content.Pose> poses() {
                                return java.util.Map.of("0",new net.nevinsky.abyssus.lib.core.editor.content.Pose(
                                    new net.nevinsky.abyssus.lib.core.editor.content.Vec3(1f,2f,3f),
                                    new net.nevinsky.abyssus.lib.core.editor.content.Quat(0f,0f,0f,1f)));
                            }
                        };
                    }
                }""",
                "OldOverlay.java" to """package legacy; public class OldOverlay implements $pkg.SceneOverlayProvider, $pkg.SceneOverlay {
                    public int positions;
                    public $pkg.SceneOverlay create(com.intellij.openapi.project.Project p, com.intellij.openapi.vfs.VirtualFile f) { return this; }
                    public void draw($pkg.OverlayView view, net.nevinsky.abyssus.lib.core.editor.pick.LineSink lines) {
                        positions = view.getContent().getEntityPositions().size();
                        lines.line(new net.nevinsky.abyssus.lib.core.editor.content.Vec3(1f,2f,3f),
                            new net.nevinsky.abyssus.lib.core.editor.content.Vec3(2f,2f,3f),
                            new net.nevinsky.abyssus.lib.core.editor.content.Rgba(1f,1f,1f,1f));
                    }
                }""",
                "Combined.java" to """package legacy; public class Combined extends OldSimulation implements $pkg.SceneOverlayProvider {
                    public $pkg.SceneOverlay create(com.intellij.openapi.project.Project p, com.intellij.openapi.vfs.VirtualFile f) { return null; }
                }""",
            ).map { (name,text) -> java.io.File(dir,name).also { it.writeText(text) } }
            val types = listOf(SceneSimulationProvider::class.java, com.intellij.openapi.project.Project::class.java,
                com.intellij.openapi.vfs.VirtualFile::class.java, com.intellij.openapi.Disposable::class.java,
                com.intellij.openapi.util.UserDataHolder::class.java, kotlin.Unit::class.java, net.nevinsky.abyssus.lib.core.editor.scene.SceneContent::class.java,
                com.badlogic.gdx.graphics.Camera::class.java)
            val cp = (System.getProperty("java.class.path").split(java.io.File.pathSeparator) +
                types.mapNotNull { it.protectionDomain.codeSource?.location?.toURI()?.let { uri -> java.io.File(uri) }?.path })
                .distinct().joinToString(java.io.File.pathSeparator)
            val compiler = ProcessBuilder(listOf(System.getProperty("abyssus.javac"), "--release", "21",
                "-classpath", cp, "-d", dir.path) + sources.map { it.path })
                .redirectErrorStream(true).start()
            val errors = compiler.inputStream.bufferedReader().readText()
            assertEquals(errors, 0, compiler.waitFor())
            java.net.URLClassLoader(arrayOf(dir.toURI().toURL()),javaClass.classLoader).use { loader ->
                val simulation = loader.loadClass("legacy.OldSimulation").getConstructor().newInstance()
                val overlay = loader.loadClass("legacy.OldOverlay").getConstructor().newInstance()
                val file = myFixture.addFileToProject("old.scene","{}").virtualFile
                assertTrue((simulation as SceneSimulationProvider).isAvailable(project,file))
                assertTrue((overlay as SceneOverlayProvider).isAvailable(project,file))
                val combined = loader.loadClass("legacy.Combined").getConstructor().newInstance()
                assertTrue((combined as SceneSimulationProvider).isAvailable(project,file))
                assertTrue((combined as SceneOverlayProvider).isAvailable(project,file))
                val request = SimulationRequest(project,file,"{}",dir,null)
                val listener = object : SimulationListener { override fun started() {} override fun failed(message: String) {} }
                assertEquals(2f,(simulation as SceneSimulationProvider).start(request,listener).poses()!!.getValue("0").position.y)
                val content = net.nevinsky.abyssus.lib.core.editor.scene.SceneContent()
                val view = OverlayView(content, net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parse("{}"),
                    null,null,com.badlogic.gdx.graphics.PerspectiveCamera(),600,false,false)
                var lines = 0
                overlay.create(project,file).draw(view, object : net.nevinsky.abyssus.lib.core.editor.pick.LineSink {
                    override fun line(from: net.nevinsky.abyssus.lib.core.editor.content.Vec3,
                        to: net.nevinsky.abyssus.lib.core.editor.content.Vec3, color: net.nevinsky.abyssus.lib.core.editor.content.Rgba) { lines++ }
                })
                assertEquals(1,lines)
                assertEquals(0,overlay.javaClass.getField("positions").getInt(overlay))
            }
        } finally { dir.deleteRecursively() }
    }
    fun testAvailabilityFollowsProjectOwnershipChangesAndNewProjects() {
        val file = myFixture.addFileToProject("ownership/scenes/Main.scene","{}").virtualFile
        val provider = PhysicsSimulationProvider()
        var available = provider.isAvailable(project,file)
        net.nevinsky.abyssus.plugin.listenSceneAvailability(project,testRootDisposable) { available = provider.isAvailable(project,file) }
        val abss = myFixture.addFileToProject("ownership/p.abss", """{"format":"abyssus","formatVersion":1,"physicsEnabled":true}""").virtualFile
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
        assertTrue(available)
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { abss.rename(this,"p.txt") }
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
        assertFalse(available)
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { abss.rename(this,"p.abss") }
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
        assertTrue(available)
        val elsewhere = myFixture.tempDirFixture.findOrCreateDir("elsewhere")
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { abss.move(this,elsewhere) }
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
        assertFalse(available)
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { abss.move(this,file.parent.parent) }
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
        assertTrue(available)
    }

    fun testPhysicsAvailabilityFollowsNativeProjectWithoutAffectingOtherProviders() {
        val abss = myFixture.addFileToProject("p/p.abss", """{"format":"abyssus","formatVersion":1}""").virtualFile
        val file = myFixture.addFileToProject("p/scenes/Main.scene", "{}").virtualFile
        val other = myFixture.addFileToProject("other/other.abss", """{"format":"abyssus","formatVersion":1}""").virtualFile
        val otherScene = myFixture.addFileToProject("other/scenes/Main.scene", "{}").virtualFile
        val loose = myFixture.addFileToProject("Loose.scene", "{}").virtualFile
        val physics = PhysicsSimulationProvider()
        val overlay = PhysicsOverlayProvider()
        assertFalse(physics.isAvailable(project,file))
        assertFalse(overlay.isAvailable(project,file))
        project.service<AbyssusProjectSettings>().setPhysicsEnabled(abss,true)
        assertTrue(physics.isAvailable(project,file))
        assertTrue(overlay.isAvailable(project,file))
        assertFalse(physics.isAvailable(project,loose))
        assertFalse(physics.isAvailable(project,otherScene))
        project.service<AbyssusProjectSettings>().setPhysicsEnabled(other,true)
        assertTrue(physics.isAvailable(project,otherScene))
        val legacy = object : SceneSimulationProvider {
            override fun start(request: SimulationRequest, listener: SimulationListener): SceneSimulation = error("unused")
        }
        assertTrue(legacy.isAvailable(project,file))
        project.service<AbyssusProjectSettings>().setPhysicsEnabled(abss,false)
        assertFalse(physics.isAvailable(project,file))
        assertTrue(legacy.isAvailable(project,file))
    }
}
