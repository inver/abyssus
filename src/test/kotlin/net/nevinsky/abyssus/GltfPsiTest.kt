/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.language.GltfFileType
import net.nevinsky.abyssus.language.psi.GltfArray
import net.nevinsky.abyssus.language.psi.GltfFile
import net.nevinsky.abyssus.language.psi.GltfObject
import net.nevinsky.abyssus.language.psi.GltfProp
import net.nevinsky.abyssus.language.psi.GltfValue

class GltfPsiTest : BasePlatformTestCase() {

    private fun parse(text: String): GltfFile =
        myFixture.configureByText(GltfFileType.INSTANCE, text) as GltfFile

    private fun errors(file: GltfFile) = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)

    fun testMinimalGltfHasNoErrors() {
        val file = parse(
            """
            {
              "asset": {"version": "2.0", "generator": null},
              "scene": 0,
              "scenes": [{"nodes": [0, 1]}],
              "nodes": [{"translation": [-1.5, 0, 2e-3], "visible": true, "hidden": false}],
              "extras": []
            }
            """.trimIndent()
        )
        assertEmpty(errors(file))
        assertNotNull(PsiTreeUtil.findChildOfType(file, GltfObject::class.java))
    }

    fun testArrayOfScalars() {
        val file = parse("""{"a": [1, "x", true, false, null, [2], {"k": 3}]}""")
        assertEmpty(errors(file))
        val array = PsiTreeUtil.findChildOfType(file, GltfArray::class.java)!!
        assertEquals(7, array.valueList.size)
    }

    fun testPropertyNamesAndValues() {
        val file = parse("""{"version": "2.0", "count": 3}""")
        val props = PsiTreeUtil.findChildrenOfType(file, GltfProp::class.java).toList()
        assertEquals(listOf("\"version\"", "\"count\""), props.map { it.fName!!.text })
        assertNotNull(props[0].value!!.string)
        assertNotNull(props[1].value!!.number)
    }

    fun testScalarLiteralsAreValues() {
        val file = parse("""{"a": true, "b": false, "c": null}""")
        val values = PsiTreeUtil.findChildrenOfType(file, GltfProp::class.java).map { it.value!! }
        assertNotNull(values[0].`true`)
        assertNotNull(values[1].`false`)
        assertNotNull(values[2].`null`)
    }

    fun testEmptyContainers() {
        assertEmpty(errors(parse("{}")))
        assertEmpty(errors(parse("[]")))
        assertEmpty(errors(parse("""{"a": {}, "b": []}""")))
    }

    fun testMissingColonReportsError() {
        assertNotEmpty(errors(parse("""{"a" 1}""")))
    }

    fun testUnterminatedStringReportsError() {
        assertNotEmpty(errors(parse("""{"a": "oops}""")))
    }

    fun testUnquotedKeyReportsError() {
        assertNotEmpty(errors(parse("""{a: 1}""")))
    }

    fun testMissingClosingBraceReportsError() {
        assertNotEmpty(errors(parse("""{"a": 1""")))
    }

    fun testMalformedNumberReportsError() {
        assertNotEmpty(errors(parse("""{"a": 01}""")))
    }

    fun testValueTypeIsExposed() {
        val file = parse("""{"a": [1]}""")
        assertTrue(PsiTreeUtil.findChildOfType(file, GltfValue::class.java) is GltfObject)
    }
}
