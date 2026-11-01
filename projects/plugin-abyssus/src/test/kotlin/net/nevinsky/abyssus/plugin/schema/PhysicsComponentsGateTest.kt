package net.nevinsky.abyssus.plugin.schema

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.plugin.filetype.AbyssusProjectSettings

class PhysicsComponentsGateTest : BasePlatformTestCase() {
    fun testPhysicsKindsFollowUnsavedSettingsAndProjectsRemainIndependent() {
        val abss = myFixture.addFileToProject("p/p.abss", """{"format":"abyssus","formatVersion":1}""").virtualFile
        val scene = myFixture.addFileToProject("p/scenes/Main.scene", """{"format":"abyssus","formatVersion":1,"ecs":{"0":{"components":{"RigidBodyComponent":{}}}}}""").virtualFile
        val other = myFixture.addFileToProject("other/scenes/Other.scene", "{}").virtualFile
        myFixture.addFileToProject("other/other.abss", """{"format":"abyssus","formatVersion":1}""")
        val root = net.nevinsky.abyssus.plugin.parsedScene(FileDocumentManager.getInstance().getDocument(scene)!!.text)
        val schemas = project.service<ComponentSchemas>()
        assertNull(schemas.editorFor(scene).read(root,0L,"RigidBodyComponent"))
        var updates = 0
        project.messageBus.connect(testRootDisposable).subscribe(ComponentSchemasListener.TOPIC, ComponentSchemasListener { updates++ })
        project.service<AbyssusProjectSettings>().setPhysicsEnabled(abss, true)
        assertNotNull(schemas.editorFor(scene).read(root,0L,"RigidBodyComponent"))
        assertTrue(schemas.editorFor(scene).missingKinds(root,0L).any { it.name == "ColliderComponent" })
        assertNull(schemas.editorFor(other).kindOf("RigidBodyComponent"))
        val doc = FileDocumentManager.getInstance().getDocument(abss)!!
        WriteCommandAction.runWriteCommandAction(project) { doc.setText(doc.text.replace("true", "false")) }
        assertNull(schemas.editorFor(scene).read(root,0L,"RigidBodyComponent"))
        assertTrue(updates >= 2)
        val before = net.nevinsky.abyssus.plugin.printScene(root)
        assertTrue(schemas.editorFor(scene).update(root,0L,"RigidBodyComponent","mass","2") is EditResult.Rejected)
        assertEquals(before,net.nevinsky.abyssus.plugin.printScene(root))
    }
}
