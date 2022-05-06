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
        assertEquals(TypeParser.createFrom("String", false), t.type)

        val a = p.getDeclarationsByName("\$a", VariableDeclaration::class.java).iterator().next()
        assertNotNull(a)
        assertEquals("\$a", a.name)
        assertEquals(TypeParser.createFrom("Object", false), a.type)

        val arr =
            p.getDeclarationsByName("\$arr", VariableDeclaration::class.java).iterator().next()
        assertNotNull(arr)
        assertEquals(TypeParser.createFrom("Object[]", false), arr.type)
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

        val test2 = p.declarations[1] as? FunctionDeclaration
        assertNotNull(test2)
        assertEquals(3, test2.parameters.size)

        val test3 = p.declarations[2] as? FunctionDeclaration
        assertNotNull(test3)
        assertEquals(2, test3.parameters.size)

        val libCallExpression =
            (test3.body as? CompoundStatement)?.statements?.get(0) as? CallExpression
        assertNotNull(libCallExpression)
        assertEquals("Write-Host", libCallExpression.name)

        var callExpression =
            (test3.body as? CompoundStatement)?.statements?.get(1) as? CallExpression
        assertNotNull(callExpression)
        assertEquals("test2", callExpression.name)
        assertEquals(test2, callExpression.invokes.iterator().next())

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

        callExpression = (test3.body as? CompoundStatement)?.statements?.get(2) as? CallExpression
        assertNotNull(callExpression)
        assertEquals("test2", callExpression.name)
        assertEquals(test2, callExpression.invokes.iterator().next())

        assertEquals(1, callExpression.arguments[2].argumentIndex)

        val s = test2.parameters.first()
        assertNotNull(s)
        assertEquals("\$value", s.name)
        assertEquals(TypeParser.createFrom("String", false), s.type)

        assertEquals("test2", test2.name)

        val compStmt = test2.body as? CompoundStatement
        assertNotNull(compStmt)
        assertNotNull(compStmt.statements)

        callExpression = compStmt.statements[0] as? CallExpression
        assertNotNull(callExpression)

        assertEquals("Write-Host", callExpression.fqn)

        literal = callExpression.arguments.first() as? Literal<*>
        assertNotNull(literal)

        assertEquals("555", literal.value)
        assertEquals(TypeParser.createFrom("int", false), literal.type)

        callExpression = (test3.body as? CompoundStatement)?.statements?.get(3) as? CallExpression
        assertNotNull(callExpression)
        // assertEquals(1, callExpression.arguments.count())
        var arr = (callExpression.arguments[0] as InitializerListExpression).initializers
        assertEquals("'function'", (arr[0] as Literal<*>).value)
        assertEquals(TypeParser.createFrom("String", false), (arr[0] as Literal<*>).type)
        assertEquals("'hi'", (arr[1] as Literal<*>).value)
        assertEquals(TypeParser.createFrom("String", false), (arr[1] as Literal<*>).type)

        callExpression = (test3.body as? CompoundStatement)?.statements?.get(4) as? CallExpression
        assertNotNull(callExpression)
        arr = (callExpression.arguments[0] as InitializerListExpression).initializers
        assertEquals("'testing'", (arr[0] as Literal<*>).value)
        assertEquals(TypeParser.createFrom("String", false), (arr[0] as Literal<*>).type)
        assertEquals("'array'", (arr[1] as Literal<*>).value)
        assertEquals(TypeParser.createFrom("String", false), (arr[1] as Literal<*>).type)
        assertEquals("5", (arr[2] as Literal<*>).value)
        assertEquals(TypeParser.createFrom("int", false), (arr[2] as Literal<*>).type)
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

    @Test
    fun testVars() {
        val topLevel = Path.of("src", "test", "resources", "powershell")
        val tu =
            TestUtils.analyzeAndGetFirstTU(
                listOf(topLevel.resolve("vars.ps1").toFile()),
                topLevel,
                true
            ) {
                it.registerLanguage(
                    PowerShellLanguageFrontend::class.java,
                    PowerShellLanguageFrontend.POWERSHELL_EXTENSIONS
                )
            }

        assertNotNull(tu)

        val p = tu.getDeclarationsByName("vars", NamespaceDeclaration::class.java).iterator().next()
        assertNotNull(p)
        assertEquals(
            "'234'",
            ((p.statements[0].declarations[0] as VariableDeclaration).initializer as Literal<*>)
                .value
        )
        assertEquals(
            "57",
            ((p.statements[1].declarations[0] as VariableDeclaration).initializer as Literal<*>)
                .value
        )
        assertEquals(
            "\$varName",
            ((p.statements[2].declarations[0] as VariableDeclaration).initializer as BinaryOperator)
                .lhs
                .name
        )
        assertEquals(
            "Plus",
            ((p.statements[2].declarations[0] as VariableDeclaration).initializer as BinaryOperator)
                .operatorCode
        )
        assertEquals(
            "\$varNum",
            ((p.statements[2].declarations[0] as VariableDeclaration).initializer as BinaryOperator)
                .rhs
                .name
        )
        assertEquals(
            "Write-Host",
            ((p.statements[3].declarations[0] as VariableDeclaration).initializer as CallExpression)
                .name
        )
        val arr =
            ((p.statements[4].declarations[0] as VariableDeclaration).initializer as
                    InitializerListExpression)
                .initializers
        assertEquals("50", (arr[0] as Literal<*>).value)
        assertEquals("20", (arr[1] as Literal<*>).value)
        assertEquals("'test'", (arr[2] as Literal<*>).value)
        assertEquals(TypeParser.createFrom("int", false), (arr[0] as Literal<*>).type)
        assertEquals(TypeParser.createFrom("String", false), (arr[2] as Literal<*>).type)
    }

    @Test
    fun testLoops() {
        val topLevel = Path.of("src", "test", "resources", "powershell")
        val tu =
            TestUtils.analyzeAndGetFirstTU(
                listOf(topLevel.resolve("loop.ps1").toFile()),
                topLevel,
                true
            ) {
                it.registerLanguage(
                    PowerShellLanguageFrontend::class.java,
                    PowerShellLanguageFrontend.POWERSHELL_EXTENSIONS
                )
            }

        assertNotNull(tu)

        val p = tu.getDeclarationsByName("loop", NamespaceDeclaration::class.java).iterator().next()

        assertNotNull(p)
        val forloop = p.statements[0] as ForStatement
        assertNotNull(forloop)

        var init = forloop.initializerStatement as DeclarationStatement
        assertNotNull(init)
        var initVar = init.declarations[0] as VariableDeclaration
        assertEquals("\$i", (initVar.name))
        assertEquals("0", ((initVar.initializer) as Literal<*>).value)

        var cond = forloop.condition as BinaryOperator
        assertNotNull(cond)
        assertEquals("\$i", cond.lhs.name)
        assertEquals("5", cond.rhs.name)
        assertEquals("-lt", cond.operatorCode)

        var it = forloop.iterationStatement as UnaryOperator
        assertNotNull(it)
        assertEquals(((it.input as DeclaredReferenceExpression).refersTo), initVar)
        assertEquals("++", it.operatorCode)

        var body = forloop.statement
        assertNotNull(body)

        /** While loop */
        val whileloop = p.statements[2] as WhileStatement
        assertNotNull(whileloop)

        cond = whileloop.condition as BinaryOperator
        assertNotNull(cond)
        assertEquals("\$i", cond.lhs.name)
        assertEquals("5", cond.rhs.name)
        assertEquals("-lt", cond.operatorCode)

        body = whileloop.statement
        assertNotNull(body)

        /** DoWhile loop */
        val doWhileloop = p.statements[4] as DoStatement
        assertNotNull(doWhileloop)

        cond = doWhileloop.condition as BinaryOperator
        assertNotNull(cond)
        assertEquals("\$i", cond.lhs.name)
        assertEquals("5", cond.rhs.name)
        assertEquals("-lt", cond.operatorCode)

        body = doWhileloop.statement
        assertNotNull(body)

        /** DoUntil loop */
        val doUntilloop = p.statements[6] as DoStatement
        assertNotNull(doWhileloop)

        cond = doUntilloop.condition as BinaryOperator
        assertNotNull(cond)
        assertEquals("\$i", cond.lhs.name)
        assertEquals("5", cond.rhs.name)
        assertEquals("-gt", cond.operatorCode)
        assertTrue(cond.code!![0] == '!')

        body = doUntilloop.statement
        assertNotNull(body)

        /** ForEach */
        val arr = (p.statements[7] as DeclarationStatement).declarations[0] as VariableDeclaration
        val forEach = p.statements[8] as ForEachStatement
        assertNotNull(forEach)
        val iterator = forEach.iterable as DeclaredReferenceExpression
        assertEquals((iterator.refersTo), arr)
        val variable = forEach.variable.declarations[0]
        assertEquals("\$a", variable.name)

        body = forEach.statement as CompoundStatement
        assertNotNull(body)
        assertEquals("Write-Host", (body.statements[0] as CallExpression).name)
        assertEquals(
            variable,
            ((((body.statements[1] as DeclarationStatement).declarations[0] as VariableDeclaration)
                        .initializer as
                        BinaryOperator)
                    .lhs as
                    DeclaredReferenceExpression)
                .refersTo as
                VariableDeclaration
        )
    }

    @Test
    fun testSwitch() {
        val topLevel = Path.of("src", "test", "resources", "powershell")
        val tu =
            TestUtils.analyzeAndGetFirstTU(
                listOf(topLevel.resolve("switch.ps1").toFile()),
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
            tu.getDeclarationsByName("switch", NamespaceDeclaration::class.java).iterator().next()
        assertNotNull(p)
        val day = p.declarations[0] as VariableDeclaration
        val switch = p.statements[1] as SwitchStatement
        assertNotNull(switch)
        assertEquals(
            day,
            (switch.selector as DeclaredReferenceExpression).refersTo as VariableDeclaration
        )
        assertEquals(7 * 2, (switch.statement as CompoundStatement).statements.size)
        assertEquals(
            "'Wednesday'",
            (((switch.statement as CompoundStatement).statements[3 * 2] as CaseStatement)
                    .caseExpression as
                    Literal<*>)
                .value
        )
        assertEquals(
            "3",
            ((switch.statement as CompoundStatement).statements[3 * 2 + 1] as Literal<*>).value
        )
    }
}
