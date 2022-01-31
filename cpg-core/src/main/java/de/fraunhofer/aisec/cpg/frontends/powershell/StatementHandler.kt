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
import de.fraunhofer.aisec.cpg.graph.statements.*
import de.fraunhofer.aisec.cpg.graph.statements.expressions.Expression
import de.fraunhofer.aisec.cpg.graph.types.TypeParser
import de.fraunhofer.aisec.cpg.graph.types.UnknownType
import de.fraunhofer.aisec.cpg.sarif.PhysicalLocation
import de.fraunhofer.aisec.cpg.sarif.Region
import java.io.File

@ExperimentalPowerShell
class StatementHandler(lang: PowerShellLanguageFrontend) :
    Handler<Statement, PowerShellNode, PowerShellLanguageFrontend>(::Statement, lang) {
    init {
        map.put(PowerShellNode::class.java, ::handleNode)
    }

    private fun handleNode(node: PowerShellNode): Statement? {
        println("STATEMENT:  ${node.type}")
        when (node.type) {
            // Handle all statements
            "NamedBlockAst" -> return handleGenericBlock(node)
            "PipelineAst" -> return handlePipelineStmt(node)
            "AssignmentStatementAst" -> return handleAssignmentStmt(node)
            "IfStatementAst" -> return handleIfStmt(node)
            "ForStatementAst" -> return handleForStmt(node)
            "WhileStatementAst" -> return handleWhileStmt(node)
            "DoWhileStatementAst" -> return handleDoWhileStmt(node)
            "DoUntilStatementAst" -> return handleDoUntilStmt(node)
            "StatementBlockAst" -> return handleStatementBlock(node)

            // "CommandAst" -> return handleCommandStmt(node)
            "CommandAst" -> return handleExpressionStmt(node)
        }
        return Statement()
    }

    private fun handlePipelineStmt(node: PowerShellNode): Statement {
        // may need to do for loop here through children
        return handleExpressionStmt(node.children!![0])
    }

    private fun handleGenericBlock(node: PowerShellNode): CompoundStatement {
        // namedBlockAst and pipelineAst falls under this generic category
        val compoundStatement = NodeBuilder.newCompoundStatement(node.code)
        this.lang.scopeManager.enterScope(compoundStatement)
        for (child in node.children!!) {
            val stmt = this.handleNode(child)
            compoundStatement.addStatement(stmt)
        }
        this.lang.scopeManager.leaveScope(compoundStatement)
        return compoundStatement
    }

    // AssignmentStatementAst
    // Need to care for everything? 4 cases of inside/not inside class/function
    // FOR NOW: assignment if new; declaredreference if old
    private fun handleAssignmentStmt(node: PowerShellNode): Statement {
        if (node.children!!.size != 2)
            print("FIX ME:  - more than 2 children in handleAssignmentStmt.")
        // Current work on single variable declaration.
        // parent passed in as the lhs, rhs are siblings instead of children relationship
        val lhs = node.children!![0] // VariableExpressionAst
        val lhsName = this.lang.getIdentifierName(lhs)
        val lhsType = node.codeType?.let { TypeParser.createFrom(it, false) }
        val ref =
            NodeBuilder.newDeclaredReferenceExpression(
                lhsName,
                lhsType ?: UnknownType.getUnknownType(),
                this.lang.getCodeFromRawNode(lhs)
            )
        val resolved = this.lang.scopeManager.resolveReference(ref)
        val inRecord = this.lang.scopeManager.isInRecord
        val inFunction = this.lang.scopeManager.isInFunction
        println("STATUS: resolved: $resolved, inClass: $inRecord, inFunction: $inFunction")
        val statement: Statement
        if (resolved != null) {
            statement = handleExpressionStmt(node) // wrap expression under a statement
        } else {
            statement = NodeBuilder.newDeclarationStatement(this.lang.getCodeFromRawNode(node))
            val variableDeclaration = this.lang.declarationHandler.handle(node)

            // statement.addDeclaration(variableDeclaration)
            statement.addToPropertyEdgeDeclaration(variableDeclaration)
            this.lang.scopeManager.addDeclaration(variableDeclaration)
        }
        return statement
    }

    private fun handleExpressionStmt(node: PowerShellNode): Expression {
        // Need to fix this? Seem to be handling other things
        return this.lang.expressionHandler.handle(node)
    }

    private fun handleIfStmt(node: PowerShellNode, counter: Int = 0): Statement? {
        val numOfChildren = node.children!!.size - 1 // 0-indexing
        if (counter >= numOfChildren) { // OOB reached - do not access counter+1
            return if (counter == numOfChildren) { //
                handleThenStmt(node.children!![counter])
            } else { // counter > numOfChildren
                null
            }
        }
        val ifStmt = NodeBuilder.newIfStatement(node.code)
        ifStmt.location =
            PhysicalLocation(
                File(node.location.file).toURI(),
                Region(
                    node.location.startLine,
                    node.location.startCol,
                    node.location.endLine,
                    node.location.endCol
                )
            )
        this.lang.scopeManager.enterScope(ifStmt)
        // condition
        ifStmt.condition = handle(node.children!![counter]) as Expression?
        // Then
        ifStmt.thenStatement = handleThenStmt(node.children!![counter + 1])
        // Else
        val elseStmt = handleIfStmt(node, counter + 2)
        if (elseStmt != null) {
            ifStmt.elseStatement = elseStmt
        }

        this.lang.scopeManager.leaveScope(ifStmt)
        return ifStmt
    }

    private fun handleThenStmt(node: PowerShellNode): CompoundStatement {
        // maybe can extract this out or refactor into compoundstmt function
        // only need to look at the 2nd children
        val compoundStatement = NodeBuilder.newCompoundStatement(node.code)
        this.lang.scopeManager.enterScope(compoundStatement)
        for (child in node.children!!) {
            // Need to change this to call another function if StatementBlockAst is used else where
            compoundStatement.addStatement(handle(child))
        }
        this.lang.scopeManager.leaveScope(compoundStatement)

        return compoundStatement
    }

    private fun handleStatementBlock(node: PowerShellNode): Statement {
        if (node.children!!.size > 1) {
            val compoundStmt = NodeBuilder.newCompoundStatement(node.code)
            this.lang.scopeManager.enterScope(compoundStmt)
            for (child in node.children!!) {
                val handled = handle(child)
                if (handled != null) {
                    compoundStmt.addStatement(handled)
                }
            }
            this.lang.scopeManager.leaveScope(compoundStmt)
        }
        return handle(node.children!![0])
    }

    private fun handleForStmt(node: PowerShellNode): ForStatement {
        val forStmt = NodeBuilder.newForStatement(node.code)
        this.lang.scopeManager.enterScope(forStmt)
        // Handle initializer - child[0] is AssignmentStatementAst
        forStmt.initializerStatement = handle(node.children!![0])
        // Handle condition - child[1] is PipelineAst
        forStmt.condition = handle(node.children!![1]) as Expression
        // Handle iteration
        forStmt.iterationStatement = handle(node.children!![2])
        // Handle body
        forStmt.statement = handle(node.children!![3])

        this.lang.scopeManager.leaveScope(forStmt)
        return forStmt
    }

    // First child is the condition, second child is the body.
    private fun handleWhileStmt(node: PowerShellNode): WhileStatement {
        val whileStmt = NodeBuilder.newWhileStatement(node.code)
        this.lang.scopeManager.enterScope(whileStmt)
        // whileStmt.conditionDeclaration = handle(node)
        whileStmt.condition = this.lang.expressionHandler.handle(node.children!![0])
        whileStmt.statement = handle(node.children!![1])
        this.lang.scopeManager.leaveScope(whileStmt)
        return whileStmt
    }

    private fun handleDoWhileStmt(node: PowerShellNode): DoStatement {
        val doStmt = NodeBuilder.newDoStatement(node.code)
        this.lang.scopeManager.enterScope(doStmt)
        doStmt.condition = this.lang.expressionHandler.handle(node.children!![0])
        doStmt.statement = handle(node.children!![1])
        this.lang.scopeManager.leaveScope(doStmt)
        return doStmt
    }

    private fun handleDoUntilStmt(node: PowerShellNode): DoStatement {
        log.debug("Handling of DoUntil Statements is currently not supported")
        val doStmt = NodeBuilder.newDoStatement(node.code)
        this.lang.scopeManager.enterScope(doStmt)
        doStmt.condition = this.lang.expressionHandler.handle(node.children!![0])
        doStmt.statement = handle(node.children!![1])
        this.lang.scopeManager.leaveScope(doStmt)
        return doStmt
    }
}
