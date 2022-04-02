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
import de.fraunhofer.aisec.cpg.TestUtils
import de.fraunhofer.aisec.cpg.TestUtils.analyzeAndGetFirstTU
import java.io.File
import java.util.List
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("experimentalPowerShell")
@ExperimentalPowerShell
class PowerShellLanguageFrontendTest {

    @Test
    fun testVariable() {
        // val file = File("src/test/resources/powershell/variableDecl.ps1")
        val file = File("src/test/resources/powershell/functionDecl.ps1")
        val tu =
            TestUtils.analyzeAndGetFirstTU(List.of(file), file.parentFile.toPath(), true) {
                it.registerLanguage(
                    PowerShellLanguageFrontend::class.java,
                    PowerShellLanguageFrontend.POWERSHELL_EXTENSIONS
                )
            }

        // val topLevel = Path.of("src", "test", "resources", "powershell")
        // val fileStr: String = ("variableDecl.ps1")

        /*val tu =
           TestUtils.analyzeAndGetFirstTU(
               listOf(topLevel.resolve(fileStr).toFile()),
               topLevel,
               true
           ) {
               it.registerLanguage(
                   PowerShellLanguageFrontend::class.java,
                   PowerShellLanguageFrontend.POWERSHELL_EXTENSIONS
               )
           }
        */
        assertNotNull(tu)
    }
}
