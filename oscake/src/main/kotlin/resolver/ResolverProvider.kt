/*
 * Copyright (C) 2021 Deutsche Telekom AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.oscake.resolver

import java.io.File
import java.util.SortedSet

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.oscake.RESOLVER_LOGGER
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.oscake.common.ActionProvider
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.ProcessingPhase

/**
 * The [ResolverProvider] gets the locations where to find the yml-files containing actions (their semantics
 * is checked while processing) and a list of possible [ResolverPackage]s is created.
 */
class ResolverProvider(val directory: File) :
    ActionProvider(directory, null, RESOLVER_LOGGER, ResolverPackage::class, ProcessingPhase.RESOLVING) {

    private var errorPrefix = ""
    private val errorSuffix = " --> resolver action ignored"

    /**
     * Checks if the action statements are logically correct and consistent
     */
    override fun checkSemantics(item: ActionPackage, fileName: String, fileStore: File?): Boolean {
        item as ResolverPackage
        errorPrefix = "[Semantics] - File: $fileName [${item.id.toCoordinates()}]: "

        if (item.resolverBlocks.isEmpty()) return loggerWarn("no resolve block found!")

        item.resolverBlocks.forEach { resolverBlock ->
            // 1. licenses must contain at least one license
            if (resolverBlock.licenses.isEmpty()) return loggerWarn("license list is empty!")
            if (resolverBlock.licenses.any { it == null }) return loggerWarn("license list contains null-values!")

            // 2. result must consist of a compound license - linked with "OR"
            if (!resolverBlock.result.split(" ").contains("OR"))
                return loggerWarn("\"result\" contains a license expression without \"OR\"!")

            // 3. result must not contain "AND"
            if (resolverBlock.result.split(" ").contains("AND"))
                return loggerWarn("\"result\" contains a license expression with \"AND\" --> not allowed!")

            // 4. scopes must contain at least one item - if not, an entry with an empty string is created
            if (resolverBlock.scopes.isEmpty()) resolverBlock.scopes.add("")
        }

        // 5. check multiple equal scopes with equal licenses
        val scopeLicenses: MutableList<Pair<String, ResolverBlock>> = mutableListOf()
        item.resolverBlocks.forEach { resolverBlock ->
            resolverBlock.scopes.forEach {
                scopeLicenses.add(Pair(it, resolverBlock))
            }
        }
        val scopeDict = scopeLicenses.groupBy { it.first }
        scopeDict.forEach { (key, list) ->
            val listOfLicenseLists: MutableList<SortedSet<String?>> = emptyList<SortedSet<String?>>().toMutableList()
            if (list.size > 1) {
                // more than one block for this scope was found
                list.forEach { pair ->
                    listOfLicenseLists.add(pair.second.licenses.toSortedSet(compareBy { it }))
                }
                if (listOfLicenseLists.size != listOfLicenseLists.distinct().size)
                    return loggerWarn("found multiple blocks for scope \"$key\" with equal licenses --> not allowed!")
            }
        }
        return true
    }

    private fun loggerWarn(msg: String): Boolean {
        logger.log("$errorPrefix $msg $errorSuffix", Level.WARN, phase = ProcessingPhase.RESOLVING)
        return false
    }
}
