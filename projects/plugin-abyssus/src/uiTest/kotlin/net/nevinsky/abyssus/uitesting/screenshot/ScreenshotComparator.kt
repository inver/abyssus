package net.nevinsky.abyssus.uitesting.screenshot

/** Decoded image data: `argb[y * width + x]`. No Swing, GL or IDE types, so it is trivially testable. */
class Pixels(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(argb.size == width * height) { "expected ${width * height} pixels, got ${argb.size}" }
    }

    operator fun get(x: Int, y: Int): Int = argb[y * width + x]
}

/** Inclusive pixel bounds of the changed region. */
data class Bounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
    val width get() = maxX - minX + 1
    val height get() = maxY - minY + 1
}

sealed interface Comparison {
    data object Identical : Comparison

    data class SizeMismatch(val expectedWidth: Int, val expectedHeight: Int, val actualWidth: Int, val actualHeight: Int) : Comparison

    /** [diff] has the actual image's size: changed pixels in opaque red over a dimmed copy of the actual image. */
    class Changed(val changedPixels: Int, val bounds: Bounds, val diff: Pixels) : Comparison
}

/** Exact comparison of decoded RGBA pixels. No tolerance and no masking: a different pixel is a failure. */
class ScreenshotComparator {
    fun compare(expected: Pixels, actual: Pixels): Comparison {
        if (expected.width != actual.width || expected.height != actual.height) {
            return Comparison.SizeMismatch(expected.width, expected.height, actual.width, actual.height)
        }
        var count = 0
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        val diff = IntArray(actual.argb.size)
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) {
                val a = actual[x, y]
                if (expected[x, y] == a) {
                    diff[y * actual.width + x] = dim(a)
                } else {
                    count++
                    diff[y * actual.width + x] = CHANGED
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (count == 0) Comparison.Identical
        else Comparison.Changed(count, Bounds(minX, minY, maxX, maxY), Pixels(actual.width, actual.height, diff))
    }

    private fun dim(argb: Int): Int {
        val r = (argb shr 16 and 0xFF) / 3
        val g = (argb shr 8 and 0xFF) / 3
        val b = (argb and 0xFF) / 3
        return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
    }

    private companion object {
        const val CHANGED = 0xFFFF0000.toInt()
    }
}
