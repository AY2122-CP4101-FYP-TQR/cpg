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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.fraunhofer.aisec.cpg.ExperimentalPowerShell
import de.fraunhofer.aisec.cpg.TranslationConfiguration
import de.fraunhofer.aisec.cpg.frontends.LanguageFrontend
import de.fraunhofer.aisec.cpg.graph.TypeManager
import de.fraunhofer.aisec.cpg.graph.declarations.TranslationUnitDeclaration
import de.fraunhofer.aisec.cpg.passes.scopes.ScopeManager
import de.fraunhofer.aisec.cpg.sarif.PhysicalLocation
import de.fraunhofer.aisec.cpg.sarif.Region
import java.io.File
import kotlin.system.exitProcess
import org.checkerframework.checker.nullness.qual.NonNull

@ExperimentalPowerShell
class PowerShellLanguageFrontend(
    config: @NonNull TranslationConfiguration,
    scopeManager: ScopeManager?
) : LanguageFrontend(config, scopeManager, ".") {

    val tudHandler = TranslationalUnitDeclarationHandler(this)
    val declarationHandler = DeclarationHandler(this)
    val statementHandler = StatementHandler(this)
    val expressionHandler = ExpressionHandler(this)
    val typeHandler = TypeHandler(this)

    var currentFileContent: String? = null
    val mapper = jacksonObjectMapper()

    companion object {
        @kotlin.jvm.JvmField var POWERSHELL_EXTENSIONS: List<String> = listOf(".ps1")
    }

    override fun parse(file: File): TranslationUnitDeclaration {
        // Check if the parser file can be found
        // val fileStr = "/home/bob/Desktop/mycpg/test/ast.ps1"
        // val file = File(fileStr)
        if (!file.exists()) {
            println("file not founddddd")
            exitProcess(0)
        }
        val parserFileStr =
            "/home/bob/Desktop/fyp/cpg/cpg-core/src/main/powershell/convertAstJson.ps1"
        val parserFile = File(parserFileStr)
        if (!file.exists()) {
            println("parserFile not found")
            exitProcess(0)
        }

        val p =
            Runtime.getRuntime().exec(arrayOf("pwsh", parserFile.absolutePath, file.absolutePath))
        val node = mapper.readValue(p.inputStream, PowerShellNode::class.java)
        TypeManager.getInstance().setLanguageFrontend(this)
        // println(node.code)
        val translationUnit = this.tudHandler.handle(node) as TranslationUnitDeclaration
        // handleComments(file, translationUnit)
        return translationUnit
    }

    // DFS type of searching
    fun getFirstChildNodeNamed(targetType: String, node: PowerShellNode): PowerShellNode? {
        if (node.type == targetType) return node

        if (node.children != null) {
            for (child in node.children!!) {
                if (child.type == targetType) {
                    return child
                } else {
                    val ret = getFirstChildNodeNamed(targetType, child)
                    if (ret != null) {
                        if (ret.type == targetType) return ret
                    }
                }
            }
        }
        return null
    }

    fun getAllLastChildren(
        node: PowerShellNode,
        list: MutableList<PowerShellNode>
    ): List<PowerShellNode> {
        if (node.children == null) {
            list.add(node)
        } else {
            for (child in node.children!!) {
                getAllLastChildren(child, list)
            }
        }
        return list.toList()
    }

    override fun <T : Any?> getCodeFromRawNode(astNode: T): String? {
        return if (astNode is PowerShellNode) {
            return astNode.code
        } else {
            null
        }
    }

    override fun <T : Any?> getLocationFromRawNode(astNode: T): PhysicalLocation? {
        return if (astNode is PowerShellNode) {

            val startLine = astNode.location.startLine
            val endLine = astNode.location.endLine
            val startCol = astNode.location.startCol
            val endCol = astNode.location.endCol

            val region = Region(startLine, startCol, endLine, endCol)
            return PhysicalLocation(File(astNode.location.file).toURI(), region ?: Region())
        } else {
            null
        }
    }

    override fun <S : Any?, T : Any?> setComment(s: S, ctx: T) {
        // not implemented
    }

    internal fun getIdentifierName(node: PowerShellNode) = this.getCodeFromRawNode(node) ?: ""
}

class Location(
    var file: String,
    var startLine: Int,
    var endLine: Int,
    var startCol: Int,
    var endCol: Int
)

class ForLoop(var init: Boolean, var condition: Boolean, var body: Boolean, var iterator: Boolean)

class PowerShellNode(
    var type: String,
    var name: String?,
    var codeType: String?,
    var code: String?,
    var location: Location,
    var children: List<PowerShellNode>?,

    // other details specific to certain AST
    var operator: String?,
    var unaryType: String?,
    var forLoop: ForLoop?
) {
    /** Returns the first child node, that represent a type, if it exists. */
    val typeChildNode: PowerShellNode?
        get() {
            return this.children?.firstOrNull {
                it.type == "TypeReference" ||
                    it.type == "AnyKeyword" ||
                    it.type == "StringKeyword" ||
                    it.type == "NumberKeyword" ||
                    it.type == "ArrayType" ||
                    it.type == "TypeLiteral"
            }
        }

    fun firstChild(type: String): PowerShellNode? {
        return this.children?.firstOrNull { it.type == type }
    }
}
