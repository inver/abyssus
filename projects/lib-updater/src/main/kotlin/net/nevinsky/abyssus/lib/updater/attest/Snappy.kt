/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.attest

/** Decodes one raw Snappy block (the format of GitHub's stored attestation bundles, `*.json.sn`). Throws on malformed input. */
fun snappyDecode(input: ByteArray, maxSize: Int = 16 * 1024 * 1024): ByteArray {
    var i = 0
    var length = 0L
    var shift = 0
    while (true) {
        if (i >= input.size || shift > 28) throw IllegalArgumentException("Bad Snappy length")
        val c = input[i++].toInt() and 0xff
        length = length or ((c and 0x7f).toLong() shl shift)
        shift += 7
        if (c and 0x80 == 0) break
    }
    if (length > maxSize) throw IllegalArgumentException("Snappy data larger than $maxSize bytes")
    val out = ByteArray(length.toInt())
    var o = 0
    fun need(n: Int) { if (i + n > input.size) throw IllegalArgumentException("Truncated Snappy data") }
    while (i < input.size) {
        val tag = input[i++].toInt() and 0xff
        when (tag and 3) {
            0 -> {
                var n = tag shr 2
                if (n >= 60) {
                    val bytes = n - 59
                    need(bytes)
                    n = 0
                    for (k in 0 until bytes) n = n or ((input[i + k].toInt() and 0xff) shl (8 * k))
                    i += bytes
                }
                n += 1
                need(n)
                if (o + n > out.size) throw IllegalArgumentException("Snappy literal overruns the output")
                System.arraycopy(input, i, out, o, n)
                i += n
                o += n
            }
            else -> {
                val n: Int
                val offset: Int
                when (tag and 3) {
                    1 -> { need(1); n = ((tag shr 2) and 7) + 4; offset = ((tag shr 5) shl 8) or (input[i++].toInt() and 0xff) }
                    2 -> { need(2); n = (tag shr 2) + 1; offset = (input[i].toInt() and 0xff) or ((input[i + 1].toInt() and 0xff) shl 8); i += 2 }
                    else -> {
                        need(4)
                        n = (tag shr 2) + 1
                        offset = (input[i].toInt() and 0xff) or ((input[i + 1].toInt() and 0xff) shl 8) or
                            ((input[i + 2].toInt() and 0xff) shl 16) or ((input[i + 3].toInt() and 0xff) shl 24)
                        i += 4
                    }
                }
                if (offset <= 0 || offset > o || o + n > out.size) throw IllegalArgumentException("Bad Snappy copy")
                for (k in 0 until n) { out[o] = out[o - offset]; o++ }
            }
        }
    }
    if (o != out.size) throw IllegalArgumentException("Snappy data shorter than declared")
    return out
}
