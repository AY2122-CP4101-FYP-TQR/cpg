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
import de.fraunhofer.aisec.cpg.frontends.TranslationException
import de.fraunhofer.aisec.cpg.graph.TypeManager
import de.fraunhofer.aisec.cpg.graph.declarations.TranslationUnitDeclaration
import de.fraunhofer.aisec.cpg.passes.scopes.ScopeManager
import de.fraunhofer.aisec.cpg.sarif.PhysicalLocation
import de.fraunhofer.aisec.cpg.sarif.Region
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
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

    @Throws(TranslationException::class)
    override fun parse(file: File): TranslationUnitDeclaration {
        TypeManager.getInstance().setLanguageFrontend(this)
        return parseInternal(file, file.path)
    }

    private fun parseInternal(codeFile: File, path: String): TranslationUnitDeclaration {
        val modulePath = Path.of("convertAstJson.ps1")

        // TODO fix path to be in src/main/powershell
        val possibleLocations =
            listOf(
                Path.of(".").resolve(modulePath),
                Path.of("../powershell").resolve(modulePath),
                Path.of("src/main/powershell").resolve(modulePath),
                Path.of("src/main/kotlin/de/fraunhofer/aisec/cpg").resolve(modulePath)
            )

        var entryScript: Path? = null
        possibleLocations.forEach {
            if (it.toFile().exists()) {
                entryScript = it.toAbsolutePath()
            }
        }

        val tu: TranslationUnitDeclaration
        try {
            println(path)
            println(entryScript)
            val p =
                Runtime.getRuntime()
                    .exec(entryScript?.let { arrayOf("pwsh", it.absolutePathString(), path) })
            val node = mapper.readValue(p.inputStream, PowerShellNode::class.java)
            tu = this.tudHandler.handle(node) as TranslationUnitDeclaration
        } catch (e: Exception) {
            throw e
        }
        return tu
    }

    // DFS type of searching
    fun getFirstChildNodeNamedViaCode(targetCode: String, node: PowerShellNode): PowerShellNode? {
        if (node.code == targetCode) return node

        if (node.children != null) {
            for (child in node.children!!) {
                if (child.code == targetCode) {
                    return child
                } else {
                    val ret = getFirstChildNodeNamedViaCode(targetCode, child)
                    if (ret != null) {
                        if (ret.code == targetCode) return ret
                    }
                }
            }
        }
        return null
    }

    // DFS type of searching
    fun getFirstChildNodeViaName(targetName: String, node: PowerShellNode): PowerShellNode? {
        if (node.name == targetName) return node

        if (node.children != null) {
            for (child in node.children!!) {
                if (child.name == targetName) {
                    return child
                } else {
                    val ret = getFirstChildNodeViaName(targetName, child)
                    if (ret != null) {
                        if (ret.name == targetName) return ret
                    }
                }
            }
        }
        return null
    }

    fun convertPSCodeType(type: String): String {
        if (type.lowercase().contains("int")) {
            return "int"
        } else if (type.lowercase().contains("string")) {
            return "String"
        } else if (type.lowercase().contains("double")) {
            return "float"
        }
        return ""
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

class loop(var init: String?, var condition: String?, var body: String?, var iterator: String?)

class ifStmt(var num: Int?, var condition: List<String>?, var body: List<String>?)

class Function(var param: List<String>, var type: List<String>, var body: String)

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
    var loop: loop?,
    var function: Function?,
    var ifStmt: ifStmt?
) {
    fun firstChild(type: String): PowerShellNode? {
        return this.children?.firstOrNull { it.type == type }
    }
}
