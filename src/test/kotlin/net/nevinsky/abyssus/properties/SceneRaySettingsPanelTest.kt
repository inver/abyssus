/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.properties

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextField
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.sceneview.SceneRayControls
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel

class SceneRaySettingsPanelTest:BasePlatformTestCase() {
    private fun named(c:Component,name:String):Component?=if(c.name==name)c else (c as? Container)?.components?.firstNotNullOfOrNull { named(it,name) }
    private fun texts(c:Component):List<String> = buildList {
        if(c is JLabel && c.text!=null) add(c.text)
        if(c is Container) c.components.forEach { addAll(texts(it)) }
    }
    private fun file(text:String="""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{}}}""")=myFixture.addFileToProject("settings.scene",text).virtualFile
    private fun view(file:com.intellij.openapi.vfs.VirtualFile)=SceneDetailsView(SceneRayControls(project) { _,_->error("Settings must not open a view") },readSceneState(file,"Settings") as PanelState.UISceneState,testRootDisposable)
    fun testDefaultsAndValidEditsWorkWithoutAViewAndSelectionDoesNotWrite() {
        val file=file();val document=FileDocumentManager.getInstance().getDocument(file)!!;val original=document.text
        val view=view(file)
        assertEquals(original,document.text)
        val field=named(view,"ray-setting-maxReflectionBounces") as JBTextField
        assertEquals("1",field.text);assertTrue(field.isEnabled)
        field.text="2";field.postActionEvent()
        assertEquals(2,SceneJson.parse(document.text)["rayTracing"]["maxReflectionBounces"].intValue())
    }
    fun testInvalidAndSupersededEditsShowErrorsWithoutOverwritingTheDocument() {
        val file=file();val document=FileDocumentManager.getInstance().getDocument(file)!!
        val view=view(file);val field=named(view,"ray-setting-targetSamplesPerPixel") as JBTextField
        val original=document.text
        field.text="1.5";field.postActionEvent();assertEquals(original,document.text)
        assertTrue((named(view,"ray-setting-targetSamplesPerPixel-error") as JLabel).text.isNotBlank())
        val fresh=view(file);val other=named(fresh,"ray-setting-targetSamplesPerPixel") as JBTextField
        other.text="4";other.postActionEvent()
        field.text="8";field.postActionEvent()
        assertEquals(4,SceneJson.parse(document.text)["rayTracing"]["targetSamplesPerPixel"].intValue())
        assertTrue((named(view,"ray-setting-targetSamplesPerPixel-error") as JLabel).text.contains("changed"))
        assertTrue("a rebuilt view keeps the explanation",(named(SceneDetailsView(SceneRayControls(project) { _,_->error("no view") },
            readSceneState(file,"Settings") as PanelState.UISceneState,testRootDisposable,conflict="targetSamplesPerPixel"),
            "ray-setting-targetSamplesPerPixel-error") as JLabel).text.contains("changed"))
    }
    fun testLabelsHelpAndRangesSeparateQualityFromWork() {
        val view=view(file())
        val shown=texts(view)
        for(label in listOf("Target samples per pixel","Maximum rays per frame","Maximum reflection bounces","Maximum refraction bounces"))
            assertTrue(shown.toString(),label in shown)
        val samples=(named(view,"ray-setting-targetSamplesPerPixel-help") as JLabel).text
        val rays=(named(view,"ray-setting-maxRaysPerFrame-help") as JLabel).text
        assertTrue(samples,samples.contains("accumulated") && samples.contains("1–4096") && samples.contains("default 256"))
        assertTrue(rays,rays.contains("submitted frame") && rays.contains("shadow") && rays.contains("1–67108864") && rays.contains("default 2097152"))
        for(key in listOf("maxReflectionBounces","maxRefractionBounces"))
            assertTrue((named(view,"ray-setting-$key-help") as JLabel).text.contains("0–16"))
        assertEquals(listOf("256","2097152","1","0"),listOf("targetSamplesPerPixel","maxRaysPerFrame","maxReflectionBounces","maxRefractionBounces")
            .map { (named(view,"ray-setting-$it") as JBTextField).text })
    }
    fun testOutOfRangeAndNonNumericEditsAreRejectedForEveryField() {
        val file=file();val document=FileDocumentManager.getInstance().getDocument(file)!!;val original=document.text
        val view=view(file)
        for((key,bad) in listOf("targetSamplesPerPixel" to "0","targetSamplesPerPixel" to "4097","maxRaysPerFrame" to "67108865",
            "maxReflectionBounces" to "-1","maxRefractionBounces" to "17","maxRefractionBounces" to "two")) {
            val field=named(view,"ray-setting-$key") as JBTextField
            field.text=bad;field.postActionEvent()
            assertEquals("$key=$bad",original,document.text)
            assertTrue((named(view,"ray-setting-$key-error") as JLabel).text.isNotBlank())
        }
    }
    fun testANonObjectSettingsBlockIsExplainedAndNotEditable() {
        val view=view(file("""{"format":"abyssus","formatVersion":1,"rayTracing":[],"ecs":{"entities":{}}}"""))
        assertTrue((named(view,"ray-settings-error") as JLabel).text.contains("settings object"))
        assertFalse((named(view,"ray-setting-maxRaysPerFrame") as JBTextField).isEnabled)
    }
    fun testMalformedSettingsAreVisibleAndExternalChangesAreRead() {
        val file=file("""{"format":"abyssus","formatVersion":1,"rayTracing":{"maxRefractionBounces":null},"ecs":{"entities":{}}}""")
        val view=view(file)
        assertTrue((named(view,"ray-setting-maxRefractionBounces-error") as JLabel).text.isNotBlank())
        val field=named(view,"ray-setting-maxRefractionBounces") as JBTextField
        field.text="2";field.postActionEvent()
        assertEquals("2",(named(view(file),"ray-setting-maxRefractionBounces") as JBTextField).text)
    }
}
