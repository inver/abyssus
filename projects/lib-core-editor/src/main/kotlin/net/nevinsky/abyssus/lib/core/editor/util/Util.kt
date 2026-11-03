/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.util

import com.badlogic.gdx.math.Vector3

/** Small pure helpers the edit classes share. */
class Util {
    companion object {
        /**
         * The first free name among `<stem><extension>`, `<stem><separator>2<extension>`, `<stem><separator>3<extension>`
         * and so on. [isTaken] says whether a candidate is already used, so the caller decides what "used" means: a set of
         * ids, a file that exists, or a case-insensitive match for file systems that ignore case.
         */
        fun uniqueName(stem: String, extension: String = "", separator: String = "-", isTaken: (String) -> Boolean): String {
            var candidate = "$stem$extension"
            var n = 2
            while (isTaken(candidate)) candidate = "$stem$separator${n++}$extension"
            return candidate
        }

        /** True when none of the three coordinates of [v] is NaN or infinite; an edit refuses a point that is not. */
        fun isFinite(v: Vector3): Boolean = v.x.isFinite() && v.y.isFinite() && v.z.isFinite()
    }
}
