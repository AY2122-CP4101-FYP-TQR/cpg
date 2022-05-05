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
            "SwitchStatementAst" -> return handleSwitchStmt(node)
            "IfStatementAst" -> return handleIfStmt(node)
            "ForStatementAst" -> return handleForStmt(node)
            "WhileStatementAst" -> return handleWhileStmt(node)
            "DoWhileStatementAst" -> return handleDoWhileStmt(node)
            "DoUntilStatementAst" -> return handleDoUntilStmt(node)
            "ForEachStatementAst" -> return handleForEachStmt(node)
            "TryStatementAst" -> return handleTryStmt(node)
            "StatementBlockAst" -> return handleStatementBlock(node)
            "BreakStatementAst" -> return handleBreakStmt(node)
            "ContinueStatementAst" -> return handleContinueStmt(node)
            "CommandAst" -> return handleExpressionStmt(node)
            "CommandExpressionAst" -> return handleExpressionStmt(node)
        }
        log.warn("STATEMENT: Not handled situations: ${node.type}")
        return Statement()
    }

    private fun handlePipelineStmt(node: PowerShellNode): Statement {
        return if (node.children!!.size == 1) {
            handleExpressionStmt(node.children!![0])
        } else {
            val compoundStatement = NodeBuilder.newCompoundStatement(node.code)
            this.lang.scopeManager.enterScope(compoundStatement)
            for (child in node.children!!) {
                val stmt = this.handleNode(child)
                compoundStatement.addStatement(stmt)
            }
            this.lang.scopeManager.leaveScope(compoundStatement)
            compoundStatement
        }
    }

    fun handleGenericBlock(node: PowerShellNode): CompoundStatement {
        // namedBlockAst falls under this generic category
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
    // 4 cases of inside/not inside class/function
    // Current implementation: Declaration if new; Declared reference if old
    private fun handleAssignmentStmt(node: PowerShellNode): Statement {
        if (node.children!!.size != 2)
            log.error("handleAssignmentStmt have ${node.children!!.size} number of children")

        // Single variable declaration only
        val varNode = node.children!![0]
        val varName = this.lang.getIdentifierName(varNode)
        val varType = varNode.codeType?.let { TypeParser.createFrom(it, false) }
        val ref =
            NodeBuilder.newDeclaredReferenceExpression(
                varName,
                varType ?: UnknownType.getUnknownType(),
                varNode.code
            )
        val resolved = this.lang.scopeManager.resolveReference(ref)

        // Class and Function required to decide if is field declaration; else not required.
        // val inRecord = this.lang.scopeManager.isInRecord
        // val inFunction = this.lang.scopeManager.isInFunction
        // println("STATUS: resolved: $resolved, inClass: $inRecord, inFunction: $inFunction")
        val statement: Statement
        if (resolved != null) {
            statement = handleExpressionStmt(node) // wrap expression under a statement
        } else {
            statement = NodeBuilder.newDeclarationStatement(this.lang.getCodeFromRawNode(node))
            val variableDeclaration = this.lang.declarationHandler.handle(node)
            statement.singleDeclaration = variableDeclaration
            // To handle multiple declarations - check the cpp declaration to do this
            // statement.declarations = variableDeclaration.asList()
            this.lang.scopeManager.addDeclaration(variableDeclaration)
        }
        return statement
    }

    private fun handleExpressionStmt(node: PowerShellNode): Expression {
        // wrapper to expression handler
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
        ifStmt.location = this.lang.getLocationFromRawNode(node)
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
        if (node.children.isNullOrEmpty()) {
            return Statement()
        }
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
            return compoundStmt
        }
        return handle(node.children!![0])
    }

    private fun handleForStmt(node: PowerShellNode): ForStatement {
        val forStmt = NodeBuilder.newForStatement(node.code)
        val forLoop = node.loop!!
        var counter = 0
        this.lang.scopeManager.enterScope(forStmt)
        // Handle initializer - child[0] is AssignmentStatementAst
        if (forLoop.init != null) {
            forStmt.initializerStatement = handle(node.children!![counter])
            counter += 1
        }
        // Handle condition - child[1] is PipelineAst
        if (forLoop.condition != null) {
            forStmt.condition = handle(node.children!![counter]) as Expression
            counter += 1
        }
        // Handle iteration - child[2] is
        if (forLoop.iterator != null) {
            forStmt.iterationStatement = handle(node.children!![counter])
            counter += 1
        }
        // Handle body - child[3] is
        if (forLoop.body != null) {
            forStmt.statement = handle(node.children!![counter])
        }
        this.lang.scopeManager.leaveScope(forStmt)
        return forStmt
    }

    private fun handleForEachStmt(node: PowerShellNode): ForEachStatement {
        if (node.children == null || node.children!!.size != 3) {
            log.warn("ForEachStmt has ${node.children!!.size} children, diff from assumption of 3")
        }
        val targetNode = node.children!![0]
        val itNode = node.children!![1]
        val stmtNode = node.children!![2]

        val forStmt = NodeBuilder.newForEachStatement(node.code)
        this.lang.scopeManager.enterScope(forStmt)
        // handle declaration
        val decl = NodeBuilder.newDeclarationStatement(targetNode.code)
        decl.location = this.lang.getLocationFromRawNode(targetNode)
        val declaration = this.lang.declarationHandler.handle(targetNode)
        decl.singleDeclaration = declaration
        this.lang.scopeManager.addDeclaration(declaration)
        forStmt.variable = decl
        // handle iterable
        val it = this.lang.expressionHandler.handle(itNode)
        forStmt.iterable = it
        // handle statement
        val stmt = this.handle(stmtNode)
        forStmt.statement = stmt
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
        log.debug(
            "Handling of DoUntil Statements is currently not supported, handling like doWhile with" +
                "the conditions inverted by adding '!' to it."
        )
        val doStmt = NodeBuilder.newDoStatement(node.code)
        this.lang.scopeManager.enterScope(doStmt)
        val condition = this.lang.expressionHandler.handle(node.children!![0])
        condition.code = "!(" + condition.code.toString() + ")"
        doStmt.condition = condition
        doStmt.statement = handle(node.children!![1])
        this.lang.scopeManager.leaveScope(doStmt)
        return doStmt
    }

    // Standard/simple switch statement can handle - cpp syntax
    private fun handleSwitchStmt(node: PowerShellNode): SwitchStatement {
        val switchStmt = NodeBuilder.newSwitchStatement(node.code)
        this.lang.scopeManager.enterScope(switchStmt)

        // switchStmt.initializerStatement
        // switchStmt.selectorDeclaration

        if (node.children != null &&
                node.children!!.isNotEmpty() &&
                node.children!![0].type == "PipelineAst"
        ) {
            val condition = node.children!![0]
            switchStmt.setSelector(this.lang.expressionHandler.handle(condition))
        }
        switchStmt.statement = handleSwitchBody(node)
        this.lang.scopeManager.leaveScope(switchStmt)
        return switchStmt
    }

    private fun handleSwitchBody(node: PowerShellNode): Statement {
        val compoundStmt = NodeBuilder.newCompoundStatement(node.code)
        this.lang.scopeManager.enterScope(compoundStmt)
        val statements =
            node.children!!.subList(1, (node.children!!.size)) // first one is the Selector
        val doneList = emptyList<Int>().toMutableList()
        for ((id, child) in statements.withIndex()) {
            if (id in doneList) continue
            val statement = NodeBuilder.newCaseStatement(child.code)
            statement.setCaseExpression(this.lang.expressionHandler.handle(statements[id + 1]))
            doneList.add(id + 1)
            compoundStmt.addStatement(statement)
            compoundStmt.addStatement(this.lang.expressionHandler.handle(child))
        }
        this.lang.scopeManager.leaveScope(compoundStmt)
        return compoundStmt
    }

    private fun handleTryStmt(node: PowerShellNode): Statement {
        val tryStatement = NodeBuilder.newTryStatement(node.code)
        lang.scopeManager.enterScope(tryStatement)
        val statement = handle(node.children!![0]) as CompoundStatement?
        val catchClauses = emptyList<CatchClause>().toMutableList()
        var finalStatement: CompoundStatement? = null

        val children = node.children!!.subList(1, (node.children!!.size))
        for (child in children) {
            if (child.type != "CatchClauseAst") {
                finalStatement = handle(child) as CompoundStatement
                break
            }
            val catchClause = this.handleCatchClause(child)
            catchClauses.add(catchClause)
        }

        tryStatement.tryBlock = statement
        tryStatement.catchClauses = catchClauses
        if (finalStatement != null) {
            tryStatement.finallyBlock = finalStatement
        }
        lang.scopeManager.leaveScope(tryStatement)
        return tryStatement
    }

    private fun handleCatchClause(node: PowerShellNode): CatchClause {
        val catchClause = NodeBuilder.newCatchClause(node.code)
        lang.scopeManager.enterScope(catchClause)

        val children = node.children!!.subList(0, (node.children!!.size - 1))
        var code = ""
        for (child in children) {
            code += child.code
            code += " "
        }

        // This is not the correct way to do it but currently the CPG library only
        //  supports CPP style of Try-Catch Statement for Catch
        val param =
            NodeBuilder.newVariableDeclaration(code, UnknownType.getUnknownType(), code, false)
        catchClause.setParameter(param)
        val body = this.handle(node.children!!.last())
        if (body is CompoundStatement) {
            catchClause.body = body
        }

        lang.scopeManager.leaveScope(catchClause)
        return catchClause
    }

    private fun handleBreakStmt(node: PowerShellNode): Statement {
        return NodeBuilder.newBreakStatement(node.code)
    }

    private fun handleContinueStmt(node: PowerShellNode): Statement {
        return NodeBuilder.newContinueStatement(node.code)
    }
}
