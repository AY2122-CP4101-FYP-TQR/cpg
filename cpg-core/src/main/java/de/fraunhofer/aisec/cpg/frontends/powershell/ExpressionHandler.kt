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

import de.fraunhofer.aisec.cpg.ExperimentalPowerShell
import de.fraunhofer.aisec.cpg.frontends.Handler
import de.fraunhofer.aisec.cpg.graph.NodeBuilder
import de.fraunhofer.aisec.cpg.graph.statements.expressions.*
import de.fraunhofer.aisec.cpg.graph.types.TypeParser
import de.fraunhofer.aisec.cpg.graph.types.UnknownType
import de.fraunhofer.aisec.cpg.sarif.PhysicalLocation
import de.fraunhofer.aisec.cpg.sarif.Region
import java.io.File

@ExperimentalPowerShell
public class ExpressionHandler(lang: PowerShellLanguageFrontend) :
    Handler<Expression, PowerShellNode, PowerShellLanguageFrontend>(::Expression, lang) {
    init {
        map.put(PowerShellNode::class.java, ::handleNode)
    }

    // May contain many duplicate AST types of statement/declaration handler as parents are passed
    // in here to do work
    // on their children that are grouped together as siblings and the children AST are not useful
    // in determining
    // which function needs to be called.
    private fun handleNode(node: PowerShellNode): Expression {
        println("EXPRESSION:  ${node.type}")
        when (node.type) {
            "AssignmentStatementAst" -> return handleAssignExpression(node)
            "CommandExpressionAst" -> return handleCommandExpression(node)
            "CommandAst" -> return handleCommand(node)
            "CommandParameterAst" -> return handleDeclaredReferenceExpression(node)
            "ConstantExpressionAst" -> return handleLiteralExpression(node)
            "StringConstantExpressionAst" -> return handleLiteralExpression(node)

            // There is no variable Expression object (only variableStmt) hence if it comes here
            // then it is for declaredReferenceExpression.
            "VariableExpressionAst" -> return handleDeclaredReferenceExpression(node)
            "BinaryExpressionAst" -> return handleBinaryExpression(node)
            "UnaryExpressionAst" -> return handleUnaryBinaryExpression(node)
        }
        return Expression()
    }

    /**
     * handles an ast representing an expression when the expression is used as the first command of
     * a pipeline. appears to just be some wrapper ast? not sure what other purpose this serves but
     * for now it just serves as another wrapper to other expressionNodes
     */
    private fun handleCommandExpression(node: PowerShellNode): Expression {
        // constantExpressionAst - The ast representing an expression when the expression is used as
        // the first command of a pipeline.
        if (node.children!!.count() != 1) println("hi more than 1 children pls fix")
        return this.handle(node.children!![0])
    }

    private fun handleLiteralExpression(node: PowerShellNode): Expression {
        var typeStr =
            when (node.type) {
                "ConstantExpressionAst" -> "int"
                "StringConstantExpressionAst" -> "str"
                // should not fall into this? if it does, add them
                else -> "unknown"
            }
        if (typeStr == "unknown") println("type is actually ${node.type} pls fix")

        val type = TypeParser.createFrom(typeStr, false)
        val value = node.code

        val lit = NodeBuilder.newLiteral(value, type, node.code)
        lit.name = node.code ?: node.name ?: ""
        lit.location =
            PhysicalLocation(
                File(node.location.file).toURI(),
                Region(
                    node.location.startLine,
                    node.location.startCol,
                    node.location.endLine,
                    node.location.endCol
                )
            )
        return lit
    }

    fun handleBinaryExpression(node: PowerShellNode): Expression {
        if (node.operator == null) println("binaryExpression has no operator?")
        var operatorCode = node.operator!!
        val binaryOperator = NodeBuilder.newBinaryOperator(operatorCode, node.code)
        val lhs = handle(node.children!![0])
        val rhs = handle(node.children!![1])

        binaryOperator.lhs = lhs
        binaryOperator.rhs = rhs
        binaryOperator.type = lhs.type ?: rhs.type
        return binaryOperator
    }

    // Only have ++ and -- according to several websites
    private fun handleUnaryBinaryExpression(node: PowerShellNode): Expression {
        val token = node.unaryType!!
        var operator: String = ""
        var postFix: Boolean = false
        var preFix: Boolean = false
        when (token) {
            "PostfixPlusPlus" -> {
                operator = "++"
                postFix = true
                preFix = false
            }
            "PostfixMinusMinus" -> {
                operator = "--"
                postFix = true
                preFix = false
            }
            "PrefixPlusPlus" -> {
                operator = "++"
                postFix = false
                preFix = true
            }
            "PrefixPlusPlus" -> {
                operator = "--"
                postFix = false
                preFix = true
            }
        }
        if (operator == "") println("FIX ME: unary operator has no operator")
        val unaryOperator = NodeBuilder.newUnaryOperator(operator, postFix, preFix, node.code)
        unaryOperator.input = handle(node.children!![0])
        return unaryOperator
    }

    // still AssignmentStatementAst
    private fun handleAssignExpression(node: PowerShellNode): Expression {
        val binaryOperator = NodeBuilder.newBinaryOperator("=", node.code)
        val lhs = handle(node.children!![0])
        val rhs = handle(node.children!![1])

        binaryOperator.lhs = lhs
        binaryOperator.rhs = rhs
        binaryOperator.type = lhs.type ?: rhs.type
        return binaryOperator
    }

    private fun handleDeclaredReferenceExpression(node: PowerShellNode): Expression {
        val name = this.lang.getIdentifierName(node)
        val type = node.codeType?.let { TypeParser.createFrom(it, false) }
        val ref =
            NodeBuilder.newDeclaredReferenceExpression(
                name,
                type ?: UnknownType.getUnknownType(),
                this.lang.getCodeFromRawNode(node)
            )
        ref.location =
            PhysicalLocation(
                File(node.location.file).toURI(),
                Region(
                    node.location.startLine,
                    node.location.startCol,
                    node.location.endLine,
                    node.location.endCol
                )
            )
        return ref
    }

    private fun handleCommand(node: PowerShellNode): Expression {
        // Check if this is a function call
        val functionCallAst = node.children!![0]
        val functionCall =
            NodeBuilder.newCallExpression(
                functionCallAst.code,
                functionCallAst.code,
                node.code,
                false
            )
        val functionDef =
            this.lang.scopeManager.resolveFunctionStopScopeTraversalOnDefinition(functionCall)
        if (functionDef.size == 1) {
            // Found that it is a function call since that function definition is found.
            return handleFunctionCallExpression(node)
        } else if (functionDef.isEmpty()
        ) { // No function declaration found, must create your own then link to it.
            // TODO find a way to differentiate between function call and calling some library
            // function (cmdlet)
            // For now assume if cannot find function call, is some cmdlet
            // return as some FC
            return handleFunctionCallExpression(node)
        } else {
            // Can add other things as CommandAst sounds like it will also be used to represent
            // other stuff
            println("FIX ME: handleCommand DO NOT have ANY function def found")
            println("Warning: Unable to identify the function called, parameters may be incorrect.")
        }
        return Expression()
    }

    private fun handleFunctionCallExpression(node: PowerShellNode): Expression {
        // First child is always the function call itself.
        val functionCallAst = node.children!![0]
        val functionCall =
            NodeBuilder.newCallExpression(
                functionCallAst.code,
                functionCallAst.code,
                node.code,
                false
            )
        // Finding the function definition to get the function params' index number
        val paramsMap: MutableMap<String, Int> = emptyMap<String, Int>().toMutableMap()
        val functionDef =
            this.lang.scopeManager.resolveFunctionStopScopeTraversalOnDefinition(functionCall)
        if (functionDef.size == 1) {
            val functionCalled = functionDef[0]
            for (parameter in functionCalled.parameters) {
                paramsMap[parameter.name] = parameter.argumentIndex
            }

            // Possible improvement link it directly to the other FC.
            val numOfChildren = node.children!!.size
            var counter = 1
            // child with odd index are just placeholders to show which param it is supposed to be
            // child with even index are the ones with values passed in as args.
            while (counter + 1 < numOfChildren) {
                val paramValue = this.handle(node.children!![counter + 1])
                val paramName = node.children!![counter].code!!.replace("-", "$")
                paramValue.argumentIndex = paramsMap[paramName]!!
                functionCall.addArgument(paramValue)
                counter += 2
            }
        } else {
            // cmdlet or cannot find the declaration
            log.warn("Did not find a declaration for \"${functionCall.name}\"")
            var params = node.children!!.subList(1, (node.children!!.size))
            for ((counter, param) in params.withIndex()) {
                val paramValue = NodeBuilder.newExpression(param.code)
                paramValue.argumentIndex = counter
                functionCall.addArgument(paramValue)
            }
        }
        return functionCall
    }
}
