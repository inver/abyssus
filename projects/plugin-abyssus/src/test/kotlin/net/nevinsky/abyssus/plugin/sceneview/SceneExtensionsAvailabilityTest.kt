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
                "OldProviders.java" to """package legacy; public class OldProviders implements $pkg.SceneSimulationProvider, $pkg.SceneOverlayProvider {
                    public $pkg.SceneSimulation start($pkg.SimulationRequest r, $pkg.SimulationListener l) { return null; }
                    public $pkg.SceneOverlay create(com.intellij.openapi.project.Project p, com.intellij.openapi.vfs.VirtualFile f) { return null; }
                }""",
            ).map { (name,text) -> java.io.File(dir,name).also { it.writeText(text) } }
            val types = listOf(SceneSimulationProvider::class.java, com.intellij.openapi.project.Project::class.java,
                com.intellij.openapi.vfs.VirtualFile::class.java, com.intellij.openapi.Disposable::class.java,
                com.intellij.openapi.util.UserDataHolder::class.java, kotlin.Unit::class.java)
            val cp = (System.getProperty("java.class.path").split(java.io.File.pathSeparator) +
                types.mapNotNull { it.protectionDomain.codeSource?.location?.toURI()?.let { uri -> java.io.File(uri) }?.path })
                .distinct().joinToString(java.io.File.pathSeparator)
            val errors = java.io.ByteArrayOutputStream()
            val result = javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,errors,
                "-classpath",cp,"-d",dir.path,*sources.map { it.path }.toTypedArray())
            assertEquals(errors.toString(),0,result)
            java.net.URLClassLoader(arrayOf(dir.toURI().toURL()),javaClass.classLoader).use { loader ->
                val instance = loader.loadClass("legacy.OldProviders").getConstructor().newInstance()
                val file = myFixture.addFileToProject("old.scene","{}").virtualFile
                assertTrue((instance as SceneSimulationProvider).isAvailable(project,file))
                assertTrue((instance as SceneOverlayProvider).isAvailable(project,file))
            }
        } finally { dir.deleteRecursively() }
    }
    fun testPhysicsAvailabilityFollowsNativeProjectWithoutAffectingOtherProviders() {
        val abss = myFixture.addFileToProject("p/p.abss", """{"format":"abyssus","formatVersion":1}""").virtualFile
        val file = myFixture.addFileToProject("p/scenes/Main.scene", "{}").virtualFile
        val loose = myFixture.addFileToProject("Loose.scene", "{}").virtualFile
        val physics = PhysicsSimulationProvider()
        val overlay = PhysicsOverlayProvider()
        assertFalse(physics.isAvailable(project,file))
        assertFalse(overlay.isAvailable(project,file))
        project.service<AbyssusProjectSettings>().setPhysicsEnabled(abss,true)
        assertTrue(physics.isAvailable(project,file))
        assertTrue(overlay.isAvailable(project,file))
        assertFalse(physics.isAvailable(project,loose))
        val legacy = object : SceneSimulationProvider {
            override fun start(request: SimulationRequest, listener: SimulationListener): SceneSimulation = error("unused")
        }
        assertTrue(legacy.isAvailable(project,file))
        project.service<AbyssusProjectSettings>().setPhysicsEnabled(abss,false)
        assertFalse(physics.isAvailable(project,file))
        assertTrue(legacy.isAvailable(project,file))
    }
}
