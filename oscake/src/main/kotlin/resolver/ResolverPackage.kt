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

import com.fasterxml.jackson.annotation.JsonProperty

import java.io.File

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*

/**
 * A [ResolverPackage] contains a resolver-action for a specific package, identified by an [id]. The instances
 * are created during processing the resolver (yml) files.
 */
internal data class ResolverPackage(
    /**
    * The [id] contains a package identification as defined in the [Identifier] class. The version information may
    * be stored as a specific version number, an IVY-expression (describing a range of versions) or may be empty (then
    * it will be valid for all version numbers).
    */
    override val id: Identifier,
    /**
    * [resolverBlocks] contains a list of [ResolverBlock]s
    */
    @get:JsonProperty("resolve") val resolverBlocks: List<ResolverBlock> = mutableListOf(),
) : ActionPackage(id) {

    /**
     * Walks through all [FileLicensing]s which contains the appropriate license information and fit into the given
     * path. Afterwards, the dir and default-scopes are regenerated.
     */
   override fun process(pack: Pack, archiveDir: File, logger: OSCakeLogger, fileStore: File?) {

       val filesToDelete = mutableListOf<String>()
       val changedFileLicensings = mutableListOf<FileLicensing>()

        // Linearize Resolver-Blocks concerning scopes
        val scopeLicenses: MutableList<Pair<String, ResolverBlock>> = mutableListOf()
        resolverBlocks.forEach { resolverBlock ->
            resolverBlock.scopes.forEach {
                scopeLicenses.add(Pair(it, resolverBlock))
            }
        }
        scopeLicenses.sortBy { it.first }
        // check each fileLicensing against licenses and its best scope
        pack.fileLicensings.forEach { fileLicensing ->
            val validResolverBlock: ResolverBlock? = getBestFit(fileLicensing, scopeLicenses)
            validResolverBlock?.let { resolverBlock ->
                filesToDelete.addAll(fileLicensing.handleCompoundLicense(resolverBlock.result))
                changedFileLicensings.add(fileLicensing)
            }
        }
       if (changedFileLicensings.isEmpty() && pack.fileLicensings.isNotEmpty()) return

       // delete affected files
       filesToDelete.forEach { archiveDir.absoluteFile.resolve(it).delete() }

       with(pack) {
           if (pack.fileLicensings.isNotEmpty()) {
               removePackageIssues().takeIf { it.isNotEmpty() }?.also {
                   logger.log(
                       "The original issues (ERRORS/WARNINGS) were removed due to Resolver actions: " +
                               it.joinToString(", "),
                       Level.WARN,
                       this.id,
                       phase = ProcessingPhase.RESOLVING
                   )
               } // because the content has changed
           }
           createConsolidatedScopes(logger, ProcessingPhase.RESOLVING, archiveDir, true)
       }
    }

   private fun getBestFit(fileLicensing: FileLicensing, scopeLicenses: MutableList<Pair<String, ResolverBlock>>):
           ResolverBlock? {
       // replace for Windows based systems
       val fileScope = File(fileLicensing.scope).path.replace("\\", "/")
       val dirScope = (File(fileLicensing.scope).parent ?: "").replace("\\", "/")

       // e.g. for fileScope is complete filename
       scopeLicenses.forEach { pair ->
           if (fileScope == pair.first &&
               fileLicensing.coversAllLicenses(pair.second.licenses.mapNotNull { it }.toSortedSet())
           )
               return pair.second
       }

       val dirList = mutableListOf<Pair<String, Int>>()
       if (fileLicensing.scope.isNotEmpty()) {
           scopeLicenses.forEach { pair ->
               if (dirScope.startsWith(pair.first) &&
                   fileLicensing.coversAllLicenses(pair.second.licenses.mapNotNull { it }.toSortedSet())
               )
                   dirList.add(Pair(pair.first, dirScope.replaceFirst(pair.first, "").length))
           }
           if (dirList.isNotEmpty()) {
               val score = dirList.minOf { it.second }
               val path = dirList.first { it.second == score }.first
               return scopeLicenses.first { it.first == path }.second
           }
       }
       return null
   }
}
