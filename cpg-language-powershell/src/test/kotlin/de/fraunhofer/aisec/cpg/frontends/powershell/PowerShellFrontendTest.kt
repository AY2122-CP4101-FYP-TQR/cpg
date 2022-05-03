/*
 * Copyright (c) 2022, Fraunhofer AISEC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *                    $$$$$$\  $$$$$$$\   $$$$$$\
 *                   $$  __$$\ $$  __$$\ $$  __$$\
 *                   $$ /  \__|$$ |  $$ |$$ /  \__|
 *                   $$ |      $$$$$$$  |$$ |$$$$\
 *                   $$ |      $$  ____/ $$ |\_$$ |
 *                   $$ |  $$\ $$ |      $$ |  $$ |
 *                   \$$$$$   |$$ |      \$$$$$   |
 *                    \______/ \__|       \______/
 *
 */
package de.fraunhofer.aisec.cpg.frontends.powershell

import de.fraunhofer.aisec.cpg.BaseTest
import de.fraunhofer.aisec.cpg.ExperimentalPowerShell
import de.fraunhofer.aisec.cpg.TestUtils
import de.fraunhofer.aisec.cpg.graph.declarations.FunctionDeclaration
import de.fraunhofer.aisec.cpg.graph.declarations.NamespaceDeclaration
import de.fraunhofer.aisec.cpg.graph.declarations.VariableDeclaration
import de.fraunhofer.aisec.cpg.graph.types.TypeParser
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("experimentalPowerShell")
@ExperimentalPowerShell
class PowerShellFrontendTest : BaseTest() {
    @Test
    fun testLiteral() {
        val topLevel = Path.of("src", "test", "resources", "powershell")
        val tu =
            TestUtils.analyzeAndGetFirstTU(
                listOf(topLevel.resolve("literal.ps1").toFile()),
                topLevel,
                true
            ) {
                it.registerLanguage(
                    PowerShellLanguageFrontend::class.java,
                    PowerShellLanguageFrontend.POWERSHELL_EXTENSIONS
                )
            }

        assertNotNull(tu)
        val p =
            tu.getDeclarationsByName("literal", NamespaceDeclaration::class.java).iterator().next()
        assertNotNull(p)
        assertEquals("literal", p.name)

        val b = p.getDeclarationsByName("\$b", VariableDeclaration::class.java).iterator().next()
        assertNotNull(b)
        assertEquals("\$b", b.name)
        assertEquals(TypeParser.createFrom("Object", false), b.type)

        val i = p.getDeclarationsByName("\$i", VariableDeclaration::class.java).iterator().next()
        assertNotNull(i)
        assertEquals("\$i", i.name)
        assertEquals(TypeParser.createFrom("int", false), i.type)

        val f = p.getDeclarationsByName("\$f", VariableDeclaration::class.java).iterator().next()
        assertNotNull(f)
        assertEquals("\$f", f.name)
        assertEquals(TypeParser.createFrom("float", false), f.type)

        val t = p.getDeclarationsByName("\$t", VariableDeclaration::class.java).iterator().next()
        assertNotNull(t)
        assertEquals("\$t", t.name)
        assertEquals(TypeParser.createFrom("str", false), t.type)

        val a = p.getDeclarationsByName("\$a", VariableDeclaration::class.java).iterator().next()
        assertNotNull(a)
        assertEquals("\$a", a.name)
        assertEquals(TypeParser.createFrom("Object", false), a.type)
    }

    @Test
    fun testFunctionDeclaration() {
        val topLevel = Path.of("src", "test", "resources", "powershell")
        val tu =
            TestUtils.analyzeAndGetFirstTU(
                listOf(topLevel.resolve("function.ps1").toFile()),
                topLevel,
                true
            ) {
                it.registerLanguage(
                    PowerShellLanguageFrontend::class.java,
                    PowerShellLanguageFrontend.POWERSHELL_EXTENSIONS
                )
            }

        assertNotNull(tu)
        val p =
            tu.getDeclarationsByName("function", NamespaceDeclaration::class.java).iterator().next()
        assertNotNull(p)

        val foo = p.declarations.first() as? FunctionDeclaration
        assertNotNull(foo)

        val bar = p.declarations[1] as? FunctionDeclaration
        assertNotNull(bar)
        assertEquals(3, bar.parameters.size)
    }
}
