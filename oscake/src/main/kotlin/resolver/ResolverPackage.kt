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
    * list of licenses to use
    */
   val licenses: List<String> = mutableListOf(),
    /**
    * resulting license = compound license info (licenses connected by "OR")
    */
   val result: String = "",
    /**
    *   list of Scopes (directory, file) - "" means the complete root directory
    */
   val scopes: List<String> = mutableListOf()
) : ActionPackage(id) {

    /**
     * defines if the deduplication process should be applied after license resolving
     */
    var dedupInResolveMode: Boolean = false

    /**
     * Walks through all [FileLicensing]s which contains the appropriate license information and fit into the given
     * path. Afterwards, the dir and default-scopes are regenerated. When [dedupInResolveMode] is set the
     * deduplication mechanism is applied to the affected [FileLicensing]s
     */
   override fun process(pack: Pack, params: OSCakeConfigParams, archiveDir: File, logger: OSCakeLogger,
                        fileStore: File?) {
       var count = 0
       val filesToDelete = mutableListOf<String>()
       val changedFileLicensings = mutableListOf<FileLicensing>()
       val licensesLower = licenses.map { it.lowercase() }.toSet()
       pack.fileLicensings.filter { it.coversOneOrAllLicenses(licensesLower) && it.fitsInPath(scopes) }
           .forEach {
               filesToDelete.addAll(it.handleCompoundLicense(result))
               changedFileLicensings.add(it)
               count++
           }
       if (count == 0) return

       pack.removeDirDefaultScopes()
       pack.createDirDefaultScopes(logger, params, ProcessingPhase.RESOLVING, true, result)

       filesToDelete.forEach {
           archiveDir.absoluteFile.resolve(it).delete()
       }

       if (dedupInResolveMode) {
           pack.deduplicateFileLicenses(archiveDir, changedFileLicensings)
           pack.deduplicateDirDirLicenses(archiveDir, true)
           pack.deduplicateDirDefaultLicenses(archiveDir, true)
       }
    }
}
