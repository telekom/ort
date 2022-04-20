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
   override fun process(pack: Pack, params: OSCakeConfigParams, archiveDir: File, logger: OSCakeLogger,
                        fileStore: File?) {

       val filesToDelete = mutableListOf<String>()
       val changedFileLicensings = mutableListOf<FileLicensing>()

       val allResolverScopes = resolverBlocks.map { it.scopes }.flatten().distinct()
       resolverBlocks.forEach { resolverBlock ->
           val licensesLower = resolverBlock.licenses.mapNotNull { it?.lowercase() }.toSortedSet()
           var resolverBlockAdministered = false
           pack.fileLicensings.filter { it.coversAllLicenses(licensesLower) &&
               it.fitsInPath(resolverBlock.scopes, allResolverScopes) }
                .forEach {
                    filesToDelete.addAll(it.handleCompoundLicense(resolverBlock.result))
                    changedFileLicensings.add(it)
                    resolverBlockAdministered = true
                }
           if (!resolverBlockAdministered && pack.fileLicensings.isNotEmpty()) logger.log("\"${resolverBlock}\" " +
                   "in file: \"${this.belongsToFile ?: "[AUTOGENERATED]"}\" was " +
                   "not used!", Level.WARN, id, phase = ProcessingPhase.RESOLVING)
       }
       if (changedFileLicensings.isEmpty() && pack.fileLicensings.isNotEmpty()) return

       // delete affected files
       filesToDelete.forEach { archiveDir.absoluteFile.resolve(it).delete() }

       with(pack) {
           if (pack.fileLicensings.isNotEmpty()) {
               removePackageIssues().takeIf { it.isNotEmpty() }?.also {
                   logger.log(
                       "The original issues (ERRORS/WARNINGS) were removed due to Resolver actions: " +
                               it.joinToString(", "), Level.WARN, this.id, phase = ProcessingPhase.RESOLVING
                   )
               } // because the content has changed
           }
           createConsolidatedScopes(logger, params, ProcessingPhase.RESOLVING, archiveDir, true)
       }
    }
}
