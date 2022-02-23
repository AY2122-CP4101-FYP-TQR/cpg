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
    Handler<Declaration, PowerShellNode, PowerShellLanguageFrontend>(::Declaration, lang) {
    init {
        map.put(PowerShellNode::class.java, ::handleNode)
    }

    private fun handleNode(node: PowerShellNode): Declaration {
        println("DECLARATION:  ${node.type}")
        when (node.type) {
            "FunctionDefinitionAst" -> return handleFunctionDeclaration(node)
            "AssignmentStatementAst" -> return handleVariableAssign(node)
            "VariableExpressionAst" -> return handleVariableDeclaration(node)
        }
        return Declaration()
    }

    private fun handleFunctionDeclaration(node: PowerShellNode): FunctionDeclaration {
        // children node here is ScriptBlockAst
        if (node.children!!.size != 1)
            log.error("FIX ME: functionDeclaration - more than 1 child in function.")
        // TODO Find cases with more than 1 child?
        val scriptBlock = node.children!![0]

        /*  if one child then handle body
         *  if more than one child then handle body + param
         *  handling of body need to be able to handle function declaration too, add to stmt.
         */
        // One child is params+attributes, Another is body

        var funcHeader: PowerShellNode? = null
        var funcBody: PowerShellNode? = null
        for (child in scriptBlock.children!!) {
            if (child.type == "ParamBlockAst") {
                funcHeader = child
                // This assumption that the body is of this type seems reasonable and accurate as
                // far as tested.
                // Represents a begin, process, end or dynamicparam block of a scriptblock - {}
            } else if (child.type == "NamedBlockAst") {
                funcBody = child
            }
        }

        val name = node.name!!
        val funcDecl = NodeBuilder.newFunctionDeclaration(name, node.code)
        this.lang.scopeManager.enterScope(funcDecl)
        // handle the function body
        // PS function return type are rather complex - hard to determine.
        // funcDecl.type = TypeParser.createFrom("string", false)
        if (funcHeader != null) funcDecl.parameters = handleFuncParamDecl(funcHeader)
        if (funcBody != null) funcDecl.body = this.lang.statementHandler.handle(funcBody)

        this.lang.scopeManager.leaveScope(funcDecl)
        this.lang.scopeManager.addDeclaration(funcDecl)
        // only in CPP they check for declarations of the same function;
        // Perfectly legal to declare another function of the same name in PS, later one will take
        // precedence
        return funcDecl
    }

    // AST here should be ParamBlockAst
    // Children can be AttributeAst - attribute for entire function
    //              or ParameterAst - which are the parameters (only extract this)
    // ParameterAst can have children too - to set type and attributes.
    // Only the type is handled, attributes are currently ignored.
    private fun handleFuncParamDecl(node: PowerShellNode): List<ParamVariableDeclaration> {
        val paramList: MutableList<ParamVariableDeclaration> = mutableListOf()
        // iterate to the ParamBlockAst which will have children, each containing param info.
        var counter = 0
        for (paramNode in node.children!!) {
            if (paramNode.type != "ParameterAst") continue
            // if there's a way to know the type then change this (PS not strictly typed)
            // val typeNode = this.lang.getFirstChildNodeNamed("TypeConstraintAst", paramNode)
            val type =
            // typeNode?.codeType?.let { TypeParser.createFrom(it, false) } ?:
            paramNode.codeType?.let { TypeParser.createFrom(it, false) }
            val paramVariableDecl =
                NodeBuilder.newMethodParameterIn(
                    paramNode.name,
                    type ?: UnknownType.getUnknownType(),
                    false,
                    paramNode.code
                )
            paramVariableDecl.location = this.lang.getLocationFromRawNode(paramNode)
            paramVariableDecl.argumentIndex = counter
            paramList.add(paramVariableDecl)
            this.lang.scopeManager.addDeclaration(paramVariableDecl)
            counter += 1
        }
        return paramList
    }

    // Does not handle multiple variable declaration
    // e.g. $a, $b, $b = 2, 4, 5
    // This function handles declaration of ONE variable.
    // LHS is variable, RHS can be anything
    private fun handleVariableAssign(node: PowerShellNode): Declaration {
        val varNode = node.children!![0]
        val variable = this.handle(varNode) as VariableDeclaration
        if (node.children!!.size == 2) {
            val valueNode = node.children!![1]
            variable.initializer = this.lang.expressionHandler.handle(valueNode)
        }
        return variable
    }

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
}
