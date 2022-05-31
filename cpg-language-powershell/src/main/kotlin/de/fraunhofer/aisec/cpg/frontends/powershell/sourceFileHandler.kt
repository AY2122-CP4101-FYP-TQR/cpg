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
import de.fraunhofer.aisec.cpg.graph.declarations.Declaration
import de.fraunhofer.aisec.cpg.graph.declarations.TranslationUnitDeclaration

@ExperimentalPowerShell
class TranslationalUnitDeclarationHandler(lang: PowerShellLanguageFrontend) :
    Handler<Declaration, PowerShellNode, PowerShellLanguageFrontend>(::ProblemDeclaration, lang) {
    init {
        map.put(PowerShellNode::class.java, ::handleSourceFile)
    }

    fun handleSourceFile(node: PowerShellNode): TranslationUnitDeclaration {
        val tu =
            NodeBuilder.newTranslationUnitDeclaration(
                node.location.file,
                this.lang.getCodeFromRawNode(node)
            )
        this.lang.scopeManager.resetToGlobal(tu)

        // For now, use filename as its namespace
        val namespace = node.location.file.split("/").last().split(".").first()
        val nsd = NodeBuilder.newNamespaceDeclaration(namespace, "")
        tu.addDeclaration(nsd)
        this.lang.scopeManager.enterScope(nsd)

        for (childNode in node.children ?: emptyList()) {
            if (childNode.type.endsWith("StatementAst") || (childNode.type == "PipelineAst")) {
                val statement = this.lang.statementHandler.handle(childNode)
                nsd.addStatement(statement)
            }
            // If not statement, everything else is a declaration - pass to declaration to handle
            else {
                val decl = this.lang.declarationHandler.handle(childNode)
                nsd.addDeclaration(decl)
            }
        }
        this.lang.scopeManager.leaveScope(nsd)
        this.lang.scopeManager.addDeclaration(nsd)
        return tu
    }
}
