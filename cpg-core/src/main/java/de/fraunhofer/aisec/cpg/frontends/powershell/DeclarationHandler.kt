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
import de.fraunhofer.aisec.cpg.sarif.PhysicalLocation
import de.fraunhofer.aisec.cpg.sarif.Region
import java.io.File

@ExperimentalPowerShell
class DeclarationHandler(lang: PowerShellLanguageFrontend) :
    Handler<Declaration, PowerShellNode, PowerShellLanguageFrontend>(::Declaration, lang) {
    init {
        map.put(PowerShellNode::class.java, ::handleNode)
    }

    private fun handleNode(node: PowerShellNode): Declaration {
        println("DECLARATION:  ${node.type}")
        when (node.type) {
            "FunctionDefinitionAst" -> return functionDeclaration(node)
            "AssignmentStatementAst" -> return handleVariableDeclaration(node)
        }
        return Declaration()
    }

    fun functionDeclaration(node: PowerShellNode): FunctionDeclaration {
        // children node here is ScriptBlockAst
        if (node.children!!.size != 1)
            println("FIX ME: functionDeclaration - more than 1 child in function.")
        // TODO Find cases with more than 1 child?
        val scriptBlock = node.children!![0]

        if (scriptBlock.children!!.size != 2)
            println("FIX ME: functionDeclaration - more than 2 child in function scriptBlock.")

        val funcHeader = scriptBlock.children!![0]
        val funcBody = scriptBlock.children!![1]

        val name = node.name!!
        val funcDecl = NodeBuilder.newFunctionDeclaration(name, node.code)
        this.lang.scopeManager.enterScope(funcDecl)
        // what am i supposed to do here?
        // handle the function body
        funcDecl.type = TypeParser.createFrom("string", false)
        funcDecl.parameters = handleFuncParamDecl(funcHeader)
        funcDecl.body = this.lang.statementHandler.handle(funcBody)

        this.lang.scopeManager.leaveScope(funcDecl)
        this.lang.scopeManager.addDeclaration(funcDecl)
        // only in CPP they check for declarations of the same function
        return funcDecl
    }

    // Creating a simple cmdlet declaration as that is done in python as well
    fun cmdletDeclaration(node: PowerShellNode) {
        // node here is CommandAst (called ONLY by function call expression)
        // Declare the function
        val funcHeader = node.children!![0]

        val cmdlet = NodeBuilder.newFunctionDeclaration(funcHeader.code!!, node.code)
        this.lang.scopeManager.enterScope(cmdlet)
        cmdlet.type = TypeParser.createFrom(funcHeader.codeType!!, false)
        cmdlet.parameters = handleArgs(node)
        // no body
        this.lang.scopeManager.leaveScope(cmdlet)
        this.lang.scopeManager.addDeclaration(cmdlet)
    }

    fun handleArgs(node: PowerShellNode): List<ParamVariableDeclaration> {
        var paramList: MutableList<ParamVariableDeclaration> = mutableListOf()
        var params = node.children!!.subList(1, (node.children!!.size))
        for ((counter, param) in params.withIndex()) {
            // for now just use this as the param values. There may be meaning in going deeper into
            // its structure?
            var type = param.codeType?.let { TypeParser.createFrom(it, false) }
            var paramVariableDecl =
                NodeBuilder.newMethodParameterIn(
                    param.name ?: param.code,
                    type ?: UnknownType.getUnknownType(),
                    false,
                    param.code
                )
            paramVariableDecl.location =
                PhysicalLocation(
                    File(param.location.file).toURI(),
                    Region(
                        param.location.startLine,
                        param.location.startCol,
                        param.location.endLine,
                        param.location.endCol
                    )
                )
            paramVariableDecl.argumentIndex = counter
            paramList.add(paramVariableDecl)
            this.lang.scopeManager.addDeclaration(paramVariableDecl)
        }
        return paramList
    }

    // AST here should be ParamBlockAst
    fun handleFuncParamDecl(node: PowerShellNode): List<ParamVariableDeclaration> {
        var paramList: MutableList<ParamVariableDeclaration> = mutableListOf()
        // iterate to the ParamBlockAst which will have children, each containing param info.
        for ((counter, paramNode) in node.children!!.withIndex()) {
            println("funcParams:  ${paramNode.type}")
            // if there's a way to know the type then change this (PS not strictly typed)
            var type = paramNode.codeType?.let { TypeParser.createFrom(it, false) }
            var paramVariableDecl =
                NodeBuilder.newMethodParameterIn(
                    paramNode.name,
                    type ?: UnknownType.getUnknownType(),
                    false,
                    paramNode.code
                )
            paramVariableDecl.location =
                PhysicalLocation(
                    File(paramNode.location.file).toURI(),
                    Region(
                        paramNode.location.startLine,
                        paramNode.location.startCol,
                        paramNode.location.endLine,
                        paramNode.location.endCol
                    )
                )
            paramVariableDecl.argumentIndex = counter
            paramList.add(paramVariableDecl)
            this.lang.scopeManager.addDeclaration(paramVariableDecl)
        }
        return paramList
    }

    private fun handleVariableDeclaration(node: PowerShellNode): Declaration {
        // Future work: need to handle multiple variable declarations - children[0] will be
        // ArrayLiteralAst
        if (node.children!!.size != 2)
            print("FIX ME:  - more than 2 children in handleVariableDecl.")
        val lhs = node.children!![0]
        val rhs = node.children!![1]

        val name = this.lang.getIdentifierName(lhs)
        val `var` =
            NodeBuilder.newVariableDeclaration(
                name,
                UnknownType.getUnknownType(),
                this.lang.getCodeFromRawNode(lhs),
                false
            )
        `var`.location = this.lang.getLocationFromRawNode(lhs)
        `var`.initializer = this.lang.expressionHandler.handle(rhs)
        return `var`
    }
}
