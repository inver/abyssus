/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.graphics.PerspectiveCamera
import net.nevinsky.abyssus.filetype.SceneJson
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.Executor
import javax.swing.SwingUtilities

class RaySettingsRevisionTest {
    init { com.badlogic.gdx.utils.GdxNativesLoader.load() }
    private val model=rayTestModel()
    private val content=SceneContent(models=listOf(AssetPlacement("entity","model",PlacementTransform.IDENTITY)))
    private val camera=PerspectiveCamera(60f,8f,8f).apply { position.set(0f,0f,2f);direction.set(0f,0f,-1f);update() }
    private fun context(target:Int)=RayFrameContext(SceneRenderParams.DEFAULT.copy(content=content,projectDir=File("project"),
        raySettings=SceneRaySettingsCodec().read(SceneJson.parse("""{"rayTracing":{"targetSamplesPerPixel":$target}}"""))),content,camera,LightSet.NONE,emptyList(),8,8,null)
    private fun feed(service:RayBackendService,id:String,executor:Executor=Executor(Runnable::run))=RayViewFeed(service.newView(id),
        RaySceneAssets({_,_->RayAssetLease({model},{null},{})},{_,_->error("terrain")}),executor)

    @Test fun settingsClearOldPublicationBeforeAStalledConversionAndReuseTheSession() {
        val device=RayFakeDevice();val service=RayFakeDevice.service("metal" to device)
        val tasks=java.util.concurrent.LinkedBlockingQueue<Runnable>()
        var stall=false
        val feed=feed(service,"view",Executor { if(stall) tasks.add(it) else it.run() })
        try {
            SwingUtilities.invokeAndWait { feed.runtime.setRequested(true) }
            RayFakeDevice.await { feed.frame(context(256));feed.runtime.latest()!=null }
            val opened=device.opened.get();stall=true
            assertNull(feed.frame(context(512)))
            assertNull(feed.runtime.latest())
            feed.frame(context(1024));assertEquals(1,tasks.size)
            stall=false;tasks.take().run()
            RayFakeDevice.await { feed.frame(context(1024));feed.runtime.latest()?.batch?.input?.settings?.targetSamples==1024 }
            assertEquals(opened,device.opened.get())
        } finally { feed.close();service.close() }
    }
    @Test fun offFutureAndTwoViewsUseTheirCurrentSavedSettingsWithoutEnablingEachOther() {
        val device=RayFakeDevice();val service=RayFakeDevice.service("metal" to device)
        val first=feed(service,"first");val second=feed(service,"second")
        try {
            first.frame(context(512));second.frame(context(512));assertEquals(0,device.probes.get())
            SwingUtilities.invokeAndWait { first.runtime.setRequested(true);second.runtime.setRequested(true) }
            RayFakeDevice.await { first.frame(context(512));second.frame(context(512));
                first.runtime.latest()?.batch?.input?.settings?.targetSamples==512 && second.runtime.latest()?.batch?.input?.settings?.targetSamples==512 }
            val future=feed(service,"future")
            try { assertNull(future.frame(context(1024)));assertFalse(future.runtime.mode.requested) } finally { future.close() }
            first.runtime.setVisible(false);first.frame(context(1024));assertNull(first.runtime.latest())
            first.runtime.setVisible(true)
            RayFakeDevice.await { first.frame(context(1024));first.runtime.latest()?.batch?.input?.settings?.targetSamples==1024 }
            second.close();assertNull(second.frame(context(1024)))
        } finally { first.close();second.close();service.close() }
    }
}
