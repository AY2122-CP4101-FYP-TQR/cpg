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
import de.fraunhofer.aisec.cpg.graph.declarations.*
import de.fraunhofer.aisec.cpg.graph.types.TypeParser
import de.fraunhofer.aisec.cpg.graph.types.UnknownType

@ExperimentalPowerShell
class DeclarationHandler(lang: PowerShellLanguageFrontend) :
    Handler<Declaration, PowerShellNode, PowerShellLanguageFrontend>(::ProblemDeclaration, lang) {
    init {
        map.put(PowerShellNode::class.java, ::handleNode)
    }

    private fun handleNode(node: PowerShellNode): Declaration {
        println("DECLARATION:  ${node.type}")
        when (node.type) {
            "AssignmentStatementAst" -> return handleVariableAssign(node)
            "FunctionDefinitionAst" -> return handleFunctionDeclaration(node)
            "VariableExpressionAst" -> return handleVariableDeclaration(node)
            "MemberExpressionAst" -> return handleNestedVariable(node)
            "ConvertExpressionAst" -> return handleNestedVariable(node)
        }
        return ProblemDeclaration()
    }

    /** Handles function declaration and its parameters Currently only supports end-blocks. */
    private fun handleFunctionDeclaration(node: PowerShellNode): FunctionDeclaration {
        val funcParam = node.function?.param
        val funcBody = node.function?.body

        val name = node.name!!
        val funcDecl = NodeBuilder.newFunctionDeclaration(name, node.code)
        this.lang.scopeManager.enterScope(funcDecl)

        if (funcParam != null) funcDecl.parameters = handleFuncParamDecl(funcParam, node)
        if (funcBody != null) {
            // Find the body node first then handle it
            val funcBodyNode = this.lang.getFirstChildNodeNamedViaCode(funcBody, node)
            funcDecl.body = this.lang.statementHandler.handle(funcBodyNode)
        }

        this.lang.scopeManager.leaveScope(funcDecl)
        this.lang.scopeManager.addDeclaration(funcDecl)
        return funcDecl
    }

    /** Handles function's parameters. If type is not declared, treated as type Object. */
    private fun handleFuncParamDecl(
        params: List<String>,
        node: PowerShellNode
    ): List<ParamVariableDeclaration> {
        val paramList: MutableList<ParamVariableDeclaration> = mutableListOf()
        var counter = 0
        for (param in params) {
            val paramNode = this.lang.getFirstChildNodeViaName(param, node)
            val type = node.function?.type?.get(counter)?.let { this.lang.convertPSCodeType(it) }
            val paramVariableDecl =
                NodeBuilder.newMethodParameterIn(
                    paramNode?.name,
                    type?.let { TypeParser.createFrom(it, false) },
                    false,
                    paramNode?.code
                )
            paramVariableDecl.location = this.lang.getLocationFromRawNode(paramNode)
            paramVariableDecl.argumentIndex = counter
            paramList.add(paramVariableDecl)
            this.lang.scopeManager.addDeclaration(paramVariableDecl)
            counter += 1
        }
        return paramList
    }

    /**
     * Handles single variable declaration where LHS is a VariableDeclaration while RHS can be
     * anything RHS is handled first to infer type for LHS. However, if RHS type is "UNKNOWN", LHS
     * type is inferred from itself.
     */
    private fun handleVariableAssign(node: PowerShellNode): Declaration {
        val varNode = node.children!![0]
        val valueNode = node.children!![1]

        val rhs = this.lang.expressionHandler.handle(valueNode)
        val tpe = rhs.type.name
        val variable: VariableDeclaration =
            if (tpe == "UNKNOWN") {
                this.handle(varNode) as VariableDeclaration
            } else {
                handleVariableDeclaration(varNode, tpe)
            }
        variable.initializer = rhs
        return variable
    }

    /** Creation of VariableDeclaration based on type inferred from LHS. */
    private fun handleVariableDeclaration(node: PowerShellNode, tpe: String): VariableDeclaration {
        val name = node.name ?: node.code
        val variable =
            NodeBuilder.newVariableDeclaration(
                name,
                TypeParser.createFrom(tpe, false),
                this.lang.getCodeFromRawNode(node),
                false
            )
        variable.location = this.lang.getLocationFromRawNode(node)
        return variable
    }

    /** Creation of VariableDeclaration based on its own type. */
    private fun handleVariableDeclaration(node: PowerShellNode): VariableDeclaration {
        val name = node.name ?: node.code
        val type = node.codeType?.let { TypeParser.createFrom(it, false) }
        val variable =
            NodeBuilder.newVariableDeclaration(
                name,
                type ?: UnknownType.getUnknownType(),
                this.lang.getCodeFromRawNode(node),
                false
            )
        variable.location = this.lang.getLocationFromRawNode(node)
        return variable
    }

    private fun handleNestedVariable(node: PowerShellNode): VariableDeclaration {
        val staticVar = node.children!![1]
        val name = staticVar.name ?: staticVar.code
        val type = node.codeType?.let { TypeParser.createFrom(it, false) }
        val variable =
            NodeBuilder.newVariableDeclaration(
                name,
                type ?: UnknownType.getUnknownType(),
                this.lang.getCodeFromRawNode(node),
                false
            )
        variable.location = this.lang.getLocationFromRawNode(node)
        return variable
    }
}
