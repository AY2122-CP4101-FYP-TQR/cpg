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
import de.fraunhofer.aisec.cpg.graph.declarations.*
import de.fraunhofer.aisec.cpg.graph.statements.*
import de.fraunhofer.aisec.cpg.graph.statements.expressions.*
import de.fraunhofer.aisec.cpg.graph.types.TypeParser
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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

        val loo = p.declarations[2] as? FunctionDeclaration
        assertNotNull(loo)
        assertEquals(2, loo.parameters.size)

        val libCallExpression =
            (loo.body as? CompoundStatement)?.statements?.get(0) as? CallExpression
        assertNotNull(libCallExpression)
        assertEquals("Write-Host", libCallExpression.name)

        var callExpression = (loo.body as? CompoundStatement)?.statements?.get(1) as? CallExpression
        assertNotNull(callExpression)
        assertEquals("test2", callExpression.name)
        assertEquals(bar, callExpression.invokes.iterator().next())

        var literal = callExpression.arguments.first() as? Literal<*>
        assertNotNull(literal)
        assertEquals("'hi'", literal.value)
        assertEquals(TypeParser.createFrom("String", false), literal.type)
        assertEquals(0, literal.argumentIndex)

        literal = callExpression.arguments[1] as? Literal<*>
        assertNotNull(literal)
        assertEquals("'some string'", literal.value)
        assertEquals(TypeParser.createFrom("String", false), literal.type)
        assertEquals(1, literal.argumentIndex)

        literal = callExpression.arguments[2] as? Literal<*>
        assertNotNull(literal)
        assertEquals("'more string'", literal.value)
        assertEquals(TypeParser.createFrom("String", false), literal.type)
        assertEquals(2, literal.argumentIndex)

        callExpression = (loo.body as? CompoundStatement)?.statements?.get(2) as? CallExpression
        assertNotNull(callExpression)
        assertEquals("test2", callExpression.name)
        assertEquals(bar, callExpression.invokes.iterator().next())

        assertEquals(1, callExpression.arguments[2].argumentIndex)

        val s = bar.parameters.first()
        assertNotNull(s)
        assertEquals("\$value", s.name)
        assertEquals(TypeParser.createFrom("String", false), s.type)

        assertEquals("test2", bar.name)

        val compStmt = bar.body as? CompoundStatement
        assertNotNull(compStmt)
        assertNotNull(compStmt.statements)

        callExpression = compStmt.statements[0] as? CallExpression
        assertNotNull(callExpression)

        assertEquals("Write-Host", callExpression.fqn)

        literal = callExpression.arguments.first() as? Literal<*>
        assertNotNull(literal)

        assertEquals("555", literal.value)
        assertEquals(TypeParser.createFrom("int", false), literal.type)
    }

    @Test
    fun testIf() {
        val topLevel = Path.of("src", "test", "resources", "powershell")
        val tu =
            TestUtils.analyzeAndGetFirstTU(
                listOf(topLevel.resolve("if.ps1").toFile()),
                topLevel,
                true
            ) {
                it.registerLanguage(
                    PowerShellLanguageFrontend::class.java,
                    PowerShellLanguageFrontend.POWERSHELL_EXTENSIONS
                )
            }

        assertNotNull(tu)

        val p = tu.getDeclarationsByName("if", NamespaceDeclaration::class.java).iterator().next()

        val `if` = p.statements[1] as? IfStatement
        assertNotNull(`if`)

        val ifThenStmt = (`if`.thenStatement as CompoundStatement).statements.first()
        assertNotNull(ifThenStmt)
        assertTrue(ifThenStmt is BinaryOperator)
        assertEquals("5", ifThenStmt.rhs.name)
        assertEquals("\$i", ifThenStmt.lhs.name)
        assertEquals("Equals", ifThenStmt.operatorCode)

        val elseif = (`if`.elseStatement as IfStatement)
        assertNotNull(elseif)
        val elseifCond = elseif.condition
        assertTrue(elseifCond is BinaryOperator)
        assertEquals("5", elseifCond.rhs.name)
        assertEquals("\$i", elseifCond.lhs.name)
        assertEquals("-lt", elseifCond.operatorCode)

        val elseThenStmt = (elseif.thenStatement as CompoundStatement).statements.first()
        assertNotNull(elseThenStmt)
        assertTrue(elseThenStmt is BinaryOperator)
        assertEquals("40", elseThenStmt.rhs.name)
        assertEquals("\$i", elseThenStmt.lhs.name)
        assertEquals("Equals", elseThenStmt.operatorCode)

        val elseStmt = (elseif.elseStatement as CompoundStatement).statements.first()
        assertNotNull(elseStmt)
        assertTrue(elseStmt is BinaryOperator)
        assertEquals("20", elseStmt.rhs.name)
        assertEquals("\$i", elseStmt.lhs.name)
        assertEquals("MinusEquals", elseStmt.operatorCode)
    }
}
