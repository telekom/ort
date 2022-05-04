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

package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import java.io.File

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance

/**
 * The class handles REUSE-compliant packages, gets a specific package [pack] and a map [scanDict],
 * which contains data about every scanned package.
 */
internal class ModeREUSE(
    /**
     * [pack] represents a specific package which is updated according to the methods.
     */
    private val pack: Pack,
    /**
     * [scanDict] is a map which contains the scanner output for every package in the project.
     */
    private val scanDict: MutableMap<Identifier,
        MutableMap<String, FileInfoBlock>>
) : ModeSelector() {

    /**
     * The method combines data from the package with data from the scanner output
     */
    override fun fetchInfosFromScanDictionary(
        sourceCodeDir: String?,
        tmpDirectory: File,
        provenance: Provenance
    ) {
        scanDict[pack.id]?.forEach { (_, fib) ->
            val isInLicensesFolder = fib.path.startsWith(getLicensesFolderPrefix(pack.packageRoot))
            if (fib.licenseTextEntries.any { it.isLicenseText && !isInLicensesFolder }) logger.log(
                "Found a license text in \"${fib.path}\" - this is outside of the LICENSES folder - will be ignored!",
                Level.WARN,
                pack.id,
                fib.path,
                phase = ProcessingPhase.PROCESS
            )

            // Phase I: inspect ./LICENSES/* folder
            if (isInLicensesFolder) handleLicenses(fib, sourceCodeDir, tmpDirectory, getHash(provenance))
            // Phase II: handle files with ending  ".license" - should be binary files
            if (!isInLicensesFolder && fib.path.endsWith(".license")) handleBinaryFiles(fib)
            // Phase III: handle all the other files
            if (!isInLicensesFolder && !fib.path.endsWith(".license")) handleDefaultFiles(fib)
            // Phase IV: handle Copyrights
            if (fib.copyrightTextEntries.size > 0) handleCopyrights(fib)
        }
        pack.createConsolidatedScopes(logger, ProcessingPhase.PROCESS, tmpDirectory, false)
    }

    /**
     * Adds a FileLicensing entry for the specified file, provided by the [fib]
     */
    private fun handleDefaultFiles(fib: FileInfoBlock) {
        FileLicensing(getPathName(pack, fib)).apply {
            fib.licenseTextEntries.filter { it.isLicenseNotice }.forEach {
                licenses.add(FileLicense(it.license))
            }
            if (licenses.size > 0) pack.fileLicensings.add(this)
        }
    }

    /**
     * Handles binary files - in REUSE every binary file has an associated ".license" file containing the
     * license information. The ".license" file is ignored and a FileLicensing is created for the binary file with
     * the provided license information from the ".licenses" file
     */
    private fun handleBinaryFiles(fib: FileInfoBlock) {
        val fileNameWithoutExtension = getPathName(pack, fib)
        // check if there is no licenseTextEntry for a file without this extension
        if (scanDict[pack.id]?.any { it.key == fileNameWithoutExtension } == true) {
            logger.log(
                "File \"${fileNameWithoutExtension}\" shows license infos although \"${fib.path}\" " +
                        "also exists! --> Files ignored!",
                Level.WARN,
                pack.id,
                fib.path,
                phase = ProcessingPhase.PROCESS
            )
            return
        }
        FileLicensing(fileNameWithoutExtension).apply {
            fib.licenseTextEntries.filter { it.isLicenseNotice }.forEach {
                licenses.add(FileLicense(it.license))
            }
            if (licenses.size > 0) pack.fileLicensings.add(this)
        }
    }

    /**
     * Copies the license files from the [sourceCodeDir] to the [createTempDir], creates a FileLicensing entry and
     * a reuseLicensing entry
     */
    private fun handleLicenses(fib: FileInfoBlock, sourceCodeDir: String?, tmpDirectory: File, provHash: String) {
        // REUSE license files should only contain one single licenseTextEntry (by definition)
        fib.licenseTextEntries.filter { it.isLicenseText && fib.licenseTextEntries.size == 1 }.forEach {
            val pathFlat = createPathFlat(pack.id, fib.path)
            val sourcePath = sourceCodeDir + "/" + pack.id.toPath("/") + "/" + provHash + "/" + fib.path
            File(sourcePath).copyTo(File(tmpDirectory.path + "/" + pathFlat))

            FileLicensing(getPathName(pack, fib)).apply {
                this.licenses.add(FileLicense(it.license, pathFlat))
                pack.fileLicensings.add(this)
            }
        }
        if (fib.licenseTextEntries.any { it.isLicenseText && fib.licenseTextEntries.size > 1 }) {
            logger.log(
                "More than one license text was found for file: ${fib.path}",
                Level.WARN,
                pack.id,
                fib.path,
                phase = ProcessingPhase.PROCESS
            )
        }
        if (fib.licenseTextEntries.any { it.isLicenseNotice }) {
            logger.log(
                "License Notice was found for a file in LICENSES folder in file: ${fib.path}",
                Level.WARN,
                pack.id,
                fib.path,
                phase = ProcessingPhase.PROCESS
            )
        }
    }

    /**
     * Handle copyrights stored in [fib] and creates copyright entries for the package
     */
    private fun handleCopyrights(fib: FileInfoBlock) =
        (
            pack.fileLicensings.firstOrNull { it.scope == getPathName(pack, fib) } ?: FileLicensing(
                getPathName(pack, fib)
            ).apply { pack.fileLicensings.add(this) }
        ).apply {
                fib.copyrightTextEntries.forEach {
                    copyrights.add(FileCopyright(it.matchedText!!))
                }
            }

    override fun postActivities(tmpDirectory: File) {
        // nothing to do for REUSE projects
    }

    /**
     * decides if the source code is needed, e.g. for instanced licenses
     */
    override fun needsSourceCode(
        scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
        pack: Pack
    ): Boolean {
        var rc = false
        scanDict[pack.id]?.forEach { (_, fib) ->
            rc = rc || fib.licenseTextEntries.any { it.isLicenseText && fib.licenseTextEntries.size == 1 }
        }
        return rc
    }
}
