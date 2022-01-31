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
import de.fraunhofer.aisec.cpg.graph.declarations.Declaration
import de.fraunhofer.aisec.cpg.graph.declarations.TranslationUnitDeclaration

@ExperimentalPowerShell
class TranslationalUnitDeclarationHandler(lang: PowerShellLanguageFrontend) :
    Handler<Declaration, PowerShellNode, PowerShellLanguageFrontend>(::Declaration, lang) {
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

        // do I create the namespace declaration here?
        // cpp and python creates one

        for (childNode in node.children ?: emptyList()) {
            if (childNode.type.endsWith("StatementAst") || (childNode.type == "PipelineAst")) {
                val statement = this.lang.statementHandler.handle(childNode)
                tu.addStatement(statement)
            }
            // If not statement, everything else is a declaration - pass to declaration to handle
            else {
                val decl = this.lang.declarationHandler.handle(childNode)
                tu.addDeclaration(decl)
            }
        }
        return tu
    }
}
