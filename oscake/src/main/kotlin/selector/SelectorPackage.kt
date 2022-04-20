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

package org.ossreviewtoolkit.oscake.selector

import com.fasterxml.jackson.annotation.JsonProperty

import java.io.File

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*

/**
 * A [SelectorPackage] contains a selector-action for a specific package, identified by an [id]. The instances
 * are created during processing the resolver (yml) files.
 */
internal data class SelectorPackage(
    /**
    * The [id] contains a package identification as defined in the [Identifier] class. The version information may
    * be stored as a specific version number, an IVY-expression (describing a range of versions) or may be empty (then
    * it will be valid for all version numbers).
    */
    override val id: Identifier,
    /**
    * [selectorBlocks] contains a list of [SelectorBlock]s
    */
    @get:JsonProperty("choices") val selectorBlocks: List<SelectorBlock> = mutableListOf(),
) : ActionPackage(id) {

    /**
     * Walks through all [FileLicensing]s which contains the specified compound license. Afterwards, the dir
     * and default-scopes are regenerated.
     */
   override fun process(pack: Pack, params: OSCakeConfigParams, archiveDir: File, logger: OSCakeLogger,
                        fileStore: File?) {

        // for special cases
        if (pack.fileLicensings.isEmpty() && pack.defaultLicensings.isEmpty()) return

        var hasChanged = false
        selectorBlocks.forEach { block ->
            pack.fileLicensings.forEach { fileLicensing ->
                fileLicensing.licenses.filter { it.license != null &&
                        CompoundOrLicense(it.license) == CompoundOrLicense(block.specified) }.forEach {
                    it.originalLicenses = CompoundOrLicense(it.license!!).toString()
                    it.license = block.selected
                    hasChanged = true
                }
            }
        }
        // log when compound license still exists --> no action was defined for this case
        pack.fileLicensings.forEach { fileLicensing ->
            if (fileLicensing.licenses.map { it.license }.any { CompoundOrLicense(it).isCompound })
                logger.log("There are still unselected compound licenses in file ${fileLicensing.scope}!",
                    Level.INFO, phase = ProcessingPhase.SELECTION)
        }

       if (!hasChanged && pack.fileLicensings.isNotEmpty()) return

       with(pack) {
           if (hasChanged) {
               removePackageIssues().takeIf { it.isNotEmpty() }?.also {
                   logger.log(
                       "The original issues (ERRORS/WARNINGS) were removed due to Selector actions: " +
                               it.joinToString(", "), Level.WARN, this.id, phase = ProcessingPhase.SELECTION
                   )
               } // because the content has changed
           }
           createConsolidatedScopes(logger, params, ProcessingPhase.SELECTION, archiveDir, true)

           // post operations for default licensings
           defaultLicensings.filter { it.path == FOUND_IN_FILE_SCOPE_CONFIGURED }.forEach { defaultLicense ->
               selectorBlocks.filter { CompoundOrLicense(it.specified) == CompoundOrLicense(defaultLicense.license) }
                   .forEach {
                       defaultLicense.originalLicenses = defaultLicense.license
                       defaultLicense.license = it.selected
                   }
           }
       }
    }
}
