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
package org.ossreviewtoolkit.oscake.deduplicator

import java.io.File

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.DEDUPLICATION_LOGGER
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*

/**
    The [PackDeduplicator] deduplicates file licenses and copyrights on all scopes for a specific [pack]age. The
    [tmpDirectory] holds a reference to the directory where the license files are stored (unzipped archive).
 */
class PackDeduplicator(private val pack: Pack, private val tmpDirectory: File,
                       private val config: OSCakeConfiguration) {

    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(DEDUPLICATION_LOGGER) }

    fun deduplicate() {
        if (pack.defaultLicensings.any { it.license?.contains(" OR ") == true } ||
            pack.fileLicensings.any { fileLicensing -> fileLicensing.licenses.any {
                it.license?.contains(" OR ") == true } }) {

            logger.log("The package \"${pack.id.toCoordinates()}\" contains compound licenses combined with" +
                    " \"OR\"! Use resolver/selector applications first!", Level.WARN, pack.id,
                phase = ProcessingPhase.DEDUPLICATION)
            return
        }

        val presFileScope = config.deduplicator?.preserveFileScopes == true
        val cmpOnlyDistinct = config.deduplicator?.compareOnlyDistinctLicensesCopyrights == true

        with(pack) {
            deduplicateFileLicenses(tmpDirectory, pack.fileLicensings, presFileScope, cmpOnlyDistinct)
            deduplicateFileCopyrights(presFileScope, cmpOnlyDistinct)
            deduplicateDirDirLicenses(tmpDirectory, false, cmpOnlyDistinct)
            deduplicateDirDirCopyrights(cmpOnlyDistinct)
            deduplicateDirDefaultLicenses(tmpDirectory, false, cmpOnlyDistinct)
            deduplicateDirDefaultCopyrights(cmpOnlyDistinct)
        }
        // clean up empty entities
        val fileLicensings2Remove = mutableListOf<FileLicensing>()
        pack.fileLicensings.forEach { fileLicensing ->
            if (fileLicensing.licenses.isEmpty() && fileLicensing.copyrights.isEmpty() &&
                fileLicensing.fileContentInArchive == null) fileLicensings2Remove.add(fileLicensing)
        }
        pack.fileLicensings.removeAll(fileLicensings2Remove)
        pack.dirLicensings.removeAll(pack.dirLicensings.filter { it.licenses.isEmpty() && it.copyrights.isEmpty() })

        if (config.deduplicator?.createUnifiedCopyrights == true) {
            makeUnifiedCopyrights()
            pack.apply {
                removeEmptyFileScopes(tmpDirectory)
                removeEmptyDirScopes(tmpDirectory)
            }
        }
        if (config.deduplicator?.keepEmptyScopes != true) {
            pack.apply {
                removeEmptyFileScopes(tmpDirectory)
                removeEmptyDirScopes(tmpDirectory)
            }
        }
    }

    /**
     * collect every copyright from file-, dir-, and default scope and assign this list to
     * the property "unifiedCopyrights" in the package; the collected copyrights are removed
     * from every scope
     */
    private fun makeUnifiedCopyrights() {
        val unified = mutableListOf<String>()
        pack.fileLicensings.filter { it.copyrights.isNotEmpty() }.forEach { fileLicensing ->
            unified.addAll(fileLicensing.copyrights.map { it.copyright })
            fileLicensing.copyrights.clear()
        }
        // take also the copyrights from dir- and default scope, because some copyrights may already have
        // been deduplicated
        pack.dirLicensings.filter { it.copyrights.isNotEmpty() }.forEach { dirLicensing ->
            unified.addAll(dirLicensing.copyrights.mapNotNull { it.copyright })
            dirLicensing.copyrights.clear()
        }
        unified.addAll(pack.defaultCopyrights.mapNotNull { it.copyright })
        pack.defaultCopyrights.clear()
        pack.unifiedCopyrights = unified.distinct().sorted().toList()
    }
}
