package net.nevinsky.abyssus.assets.sky.hdr

import net.nevinsky.abyssus.core.FileLoader
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyexr.EXRHeader
import org.lwjgl.util.tinyexr.EXRImage
import org.lwjgl.util.tinyexr.EXRVersion
import org.lwjgl.util.tinyexr.TinyEXR

class ExrLoader(private val fileLoader: FileLoader) {
    fun loadExr(assetName: String, fileName: String?): EXRImage {
        val file = fileLoader.loadFile(assetName, fileName)

        val version = EXRVersion.create()
        var ret: Int = TinyEXR.ParseEXRVersionFromFile(version, file.absolutePath)
        if (ret != TinyEXR.TINYEXR_SUCCESS) {
            throw IllegalStateException("Invalid EXR file: $file")
        }

        if (version.multipart()) {
            version.free()
            throw IllegalStateException("Multipart EXR files are not supported")
        }

        val header = EXRHeader.create()
        TinyEXR.InitEXRHeader(header)

        MemoryStack.stackPush().use { stack ->
            val errorPtr = stack.mallocPointer(1)
            ret = TinyEXR.ParseEXRHeaderFromFile(header, version, file.absolutePath, errorPtr)
            if (ret != TinyEXR.TINYEXR_SUCCESS) {
                header.free()
                version.free()
                throw IllegalStateException("EXR header parse error: " + MemoryUtil.memUTF8(errorPtr.get(0)))
            }

            val image: EXRImage = EXRImage.create()
            TinyEXR.InitEXRImage(image)

            ret = TinyEXR.LoadEXRImageFromFile(image, header, file.absolutePath, errorPtr)
            if (ret != TinyEXR.TINYEXR_SUCCESS) {
                image.free()
                header.free()
                version.free()
                throw IllegalStateException("EXR image load error: " + MemoryUtil.memUTF8(errorPtr.get(0)))
            }

            header.free()
            version.free()
            return image
        }
    }
}