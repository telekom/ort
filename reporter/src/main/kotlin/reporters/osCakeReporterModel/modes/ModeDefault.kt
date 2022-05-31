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
@file:Suppress("TooManyFunctions")
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.modes

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.FileSystems

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.CopyrightTextEntry
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.DefaultLicense
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.FileCopyright
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.FileInfoBlock
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.FileLicense
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.FileLicensing
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.LicenseTextEntry
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Pack
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.TextEntry
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.config.OSCakeConfigParams
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.FOUND_IN_FILE_SCOPE_DECLARED
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.ProcessingPhase
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.ScopeLevel
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.createPathFlat
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.deduplicateFileName
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.getHash
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.getPathName
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.getRidOfCommentSymbols
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.getScopeLevel
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.isInstancedLicense

/**
 * The class handles non-REUSE-compliant packages, gets a specific package [pack] and a map [scanDict],
 * which contains data about every scanned package.
 */
internal class ModeDefault(
    /**
     * [pack] represents a specific package which is updated according to the methods.
     */
    private val pack: Pack,
    /**
     * [scanDict] is a map which contains the scanner output for every package in the project.
     */
    private val scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
    /**
     * [reporterInput] provides information about the analyzed and scanned packages (from ORT)
     */
    private val reporterInput: ReporterInput
) : ModeSelector() {

    /**
     * The method creates [FileLicensing]s for each file containing license information. Depending on the
     * scopeLevel (FILE, DIR, or DEFAULT) additional Licensings are added to the dirLicensings or defaultLicensings
     * lists. Information about copyrights is transferred from the fib (file info block) to the [pack].
     */
    override fun fetchInfosFromScanDictionary(sourceCodeDir: String?, tmpDirectory: File, provenance: Provenance) {
        /* Phase I: go through all files in this package
         *       - step 1:   if fileName opens a dir- or default-scope, copy the original file to the archive and
         *                   create a fileLicensing to set fileContentInArchive accordingly
         *       - step 2:   for each license text entry for a specific file, generate a FileLicense and depending on
         *                   the flag is_license_text = true, get the license text from the specific source and
         *                   write a file which is referenced by "licenseTextInArchive" into the archive
         */
        phaseI(sourceCodeDir, tmpDirectory, provenance)

        /* Phase II:   copy archived files from scanner archive - and insert/update the fileLicensing entry
         *              (Info: files are archived from scanner, if the filename matches a pattern in ort.conf)
         *              This step is taken for completeness only, because normally the oscake patterns include
         *              the ORT scope patterns
         */
        phaseII(tmpDirectory)

        /*
         * Phase III: transfer Copyright text entries
         */
        phaseIII()

        /* Phase IV:    based on the FileLicenses, the default- and dir-scopes are created when the filename matches
         *              the oscake.scopePatterns
         */
        pack.createConsolidatedScopes(logger, ProcessingPhase.PROCESS, tmpDirectory, false)
    }

    private fun phaseI(sourceCodeDir: String?, tmpDirectory: File, provenance: Provenance) {
        val provHash = getHash(provenance)
        scanDict[pack.id]?.forEach { (fileName, fib) ->
            val scopeLevel = getScopeLevel(fileName, pack.packageRoot, false)
            // if fileName opens a dir- or default-scope, copy the original file to the archive
            // and create a fileLicensing to set fileContentInArchive accordingly
            if (scopeLevel == ScopeLevel.DEFAULT || scopeLevel == ScopeLevel.DIR) {
                FileLicensing(getPathName(pack, fib)).apply {
                    val pathFlat = createPathFlat(pack.id, fib.path)
                    File(sourceCodeDir + "/" + pack.id.toPath("/") + "/" + provHash + "/" + fib.path)
                        .also {
                            if (it.exists()) {
                                it.copyTo(File(tmpDirectory.path + "/" + pathFlat))
                                this.fileContentInArchive = pathFlat
                                pack.fileLicensings.add(this)
                            } else logger.log(
                                "File \"${it.name}\" not found during creation of \"File Licensings\"",
                                Level.ERROR,
                                pack.id,
                                phase = ProcessingPhase.PROCESS
                            )
                        }
                }
            }
            // sort necessary for Wekan #85 - priority logic for handling LicenseTextEntries with equal licenses
            fib.licenseTextEntries.sortedWith(LicenseTextEntry).forEach { licenseTextEntry ->
                val path = getPathName(pack, fib)
                val fileLicensing = pack.fileLicensings.firstOrNull { it.scope == path } ?: FileLicensing(path)

                if (fileLicensing.licenses.none {
                        it.license == licenseTextEntry.license && it.startLine == licenseTextEntry.startLine
                    }
                ) {
                    var dedupFileName: String? = null
                    if (licenseTextEntry.isLicenseText)
                        dedupFileName = writeLicenseText(licenseTextEntry, fib, sourceCodeDir, tmpDirectory, provHash)
                    fileLicensing.licenses.add(
                        FileLicense(licenseTextEntry.license, dedupFileName, licenseTextEntry.startLine)
                    )
                }
                if (pack.fileLicensings.none { it.scope == path }) pack.fileLicensings.add(fileLicensing)
            }
        }
    }

    /**
     * Add file from scanner archive if no license file already is assigned
     */
    private fun phaseII(tmpDirectory: File) =
        reporterInput.licenseInfoResolver.resolveLicenseFiles(pack.id).files.forEach {
            var path = it.path.replace("\\", "/")
            if (it.path.startsWith(pack.packageRoot) && pack.packageRoot != "") path = path.replaceFirst(
                pack.packageRoot, ""
            ).substring(1)

            val fl = pack.fileLicensings.firstOrNull { lic -> lic.scope == path } ?: FileLicensing(path).apply {
                licenses.add(FileLicense(null))
                pack.fileLicensings.add(this)
            }

            if (fl.fileContentInArchive == null) {
                fl.fileContentInArchive = createPathFlat(pack.id, it.path) + "_archived"
                it.file.copyTo(File(tmpDirectory.path + "/" + fl.fileContentInArchive))
            } else {
                if (fl.licenses.isEmpty()) {
                    fl.licenses.add(FileLicense(null))
                }
            }
        }

    /**
     * Add copyright entries to FileLicensing(s)
     */
    private fun phaseIII() {
        scanDict[pack.id]?.filter { (_, fib) -> fib.copyrightTextEntries.size > 0 }?.forEach { (_, fib) ->
            (
                pack.fileLicensings.firstOrNull { it.scope == getPathName(pack, fib) } ?:
                FileLicensing(getPathName(pack, fib)).apply { pack.fileLicensings.add(this) }
            ).apply {
                    fib.copyrightTextEntries.forEach {
                            copyrights.add(FileCopyright(it.matchedText!!))
                        }
                    }
        }
    }

    /**
     *  Depending on the flag "[licenseTextEntry].isInstancedLicense" the license text is generated and written
     *  to disk.
     */
    private fun writeLicenseText(
        licenseTextEntry: LicenseTextEntry,
        fib: FileInfoBlock,
        sourceCodeDir: String?,
        tmpDirectory: File,
        provHash: String
    ): String? {
        try {
            val genText =
                if (licenseTextEntry.isInstancedLicense) generateInstancedLicenseText(
                    pack, fib, sourceCodeDir, licenseTextEntry, provHash
                )
                else generateNonInstancedLicenseText(pack, fib, sourceCodeDir, licenseTextEntry, provHash)
            genText?.let {
                val dedupFileName = deduplicateFileName(
                    tmpDirectory.path + "/" + createPathFlat(pack.id, fib.path, licenseTextEntry.license)
                )
                File(dedupFileName).apply {
                    this.writeText(it)
                    return this.relativeTo(File(tmpDirectory.path)).name
                }
            }
        } catch (ex: FileNotFoundException) {
            logger.log(
                "File not found: ${ex.message}  while generating license texts!",
                Level.ERROR,
                pack.id,
                phase = ProcessingPhase.PROCESS
            )
        }
        return null
    }

    /**
     * The method calculates the first and the last line of the requested license text and returns it (an instanced
     * license must begin with a copyright statement).
     */
    private fun generateInstancedLicenseText(
        pack: Pack,
        fileInfoBlock: FileInfoBlock,
        sourceCodeDir: String?,
        licenseTextEntry: LicenseTextEntry,
        provHash: String
    ): String? {

        val fileName = sourceCodeDir + "/" + pack.id.toPath("/") + "/" + provHash + "/" + fileInfoBlock.path
        val copyrightStartLine = getCopyrightStartline(fileInfoBlock, licenseTextEntry, fileName)
        if (copyrightStartLine == null) {
            logger.log(
                "No Copyright-Startline found in ${fileInfoBlock.path}",
                Level.ERROR,
                pack.id,
                fileInfoBlock.path,
                phase = ProcessingPhase.PROCESS
            )
            return null
        }
        if (copyrightStartLine > licenseTextEntry.endLine) {
            logger.log(
                "Line markers $copyrightStartLine : ${licenseTextEntry.endLine} not valid in: ${fileInfoBlock.path}",
                Level.ERROR,
                pack.id,
                fileInfoBlock.path,
                phase = ProcessingPhase.PROCESS
            )
            return null
        }
        return getLinesFromFile(fileName, copyrightStartLine, licenseTextEntry.endLine)
    }

    /**
     * Returns the text from file ([path]) beginning at [startLine] to [endLine]
     */
    private fun getLinesFromFile(path: String, startLine: Int, endLine: Int): String =
        File(path).readLines()
            .slice(startLine - 1 until endLine)
            .joinToString(separator = System.lineSeparator()) { it.getRidOfCommentSymbols() }

    /**
     * The copyright statement must be found above of the license text [licenseTextEntry]; therefore, search for the
     * smallest startline of a copyright entry without crossing another license text
     */
    private fun getCopyrightStartline(
        fileInfoBlock: FileInfoBlock,
        licenseTextEntry: LicenseTextEntry,
        fileName: String
    ): Int? {
        val completeList = fileInfoBlock.licenseTextEntries.filter { it.isLicenseText }.toMutableList<TextEntry>()
        completeList.addAll(fileInfoBlock.copyrightTextEntries)
        completeList.sortByDescending { it.startLine }
        var found = false
        var line: Int? = null
        var lastLicenseTextEntry: LicenseTextEntry? = null

        loop@ for (lte in completeList) {
            if (found && lte is CopyrightTextEntry) line = lte.startLine
            if (found && lte is LicenseTextEntry) {
                break@loop
            }
            if (lte is LicenseTextEntry) lastLicenseTextEntry = lte

            if (lte == licenseTextEntry) found = true
        }
        /* special case #1: the scanner does not identify the line "Copyright (c) <year> <copyright holders>" as a
        valid copyright entry; therefore, no CopyrightTextEntry is found immediately before the LicenseTextEntry.
        In order to solve this case, the license text is taken from LicenseTextEntry.startLine-2 till
        LicenseTextEntry.endLine. If one of the two leading lines contains the word "Copyright", then this is the
        starting line.
         */
        line ?: run {
            lastLicenseTextEntry?.let {
                var diff = 2
                if (it.startLine == 1) diff = 0
                if (it.startLine == 2) diff = 1
                val copyrightStartLine = it.startLine - diff
                if (diff > 0) {
                    val strArr = getLinesFromFile(fileName, copyrightStartLine, it.endLine).split("\n")
                    if (strArr.size > 2) {
                        if (strArr[1].contains("Copyright")) line = copyrightStartLine + 1
                        if (strArr[0].contains("Copyright")) line = copyrightStartLine
                    }
                }
            }
        }
        /* special case #2: sometimes the scanner identifies a license text correctly, but the text itself includes
        already the copyright, therefore the standard mechanism (copyright start line is lower than the start line of
        the license text) does not work. Solution: find copyright (start and endline) inside a license text entry.
         */
        line ?: lastLicenseTextEntry?.run {
            if (completeList.filterIsInstance<CopyrightTextEntry>().any {
                    it.startLine >= lastLicenseTextEntry.startLine && it.endLine <= lastLicenseTextEntry.endLine
                }
            ) line = lastLicenseTextEntry.startLine
        }

        return line
    }

    /**
     * The method provides the license text for non-instanced licenses directly from the source file.
     */
    private fun generateNonInstancedLicenseText(
        pack: Pack,
        fileInfoBlock: FileInfoBlock,
        sourceCodeDir: String?,
        licenseTextEntry: LicenseTextEntry,
        provHash: String
    ): String? {
        val textBlockList = fileInfoBlock.licenseTextEntries.filter {
            it.isLicenseText && it.license == licenseTextEntry.license
        }.toMutableList<TextEntry>()
        if (textBlockList.size > 0) {
            textBlockList.sortBy { it.startLine }
            val fileName = sourceCodeDir + "/" + pack.id.toPath("/") + "/" + provHash + "/" + fileInfoBlock.path
            return getLinesFromFile(fileName, textBlockList[0].startLine, textBlockList[textBlockList.size - 1].endLine)
        }
        return null
    }

    /**
     * if no default licensings exist a default entry based on "declared_license" (analyzer_results.yml) is created;
     * report if multiple different licenses in default- or dir-scope are found.
     */
    override fun postActivities(tmpDirectory: File) {
        if (pack.defaultLicensings.size == 0) prepareEntryForScopeDefault(pack, reporterInput)

        pack.defaultLicensings.mapNotNull { it.license }.distinct().takeIf { it.size > 1 }?.let {
            logger.log(
                "DefaultScope: more than one license found: ${it.joinToString(" & ")} dual licensed " +
                        "or multiple licenses",
                Level.WARN,
                pack.id,
                phase = ProcessingPhase.POST
            )
        }
        // check dirScope
        pack.dirLicensings.forEach { dirLicensing ->
            dirLicensing.licenses.map { it.license }.distinct().takeIf { it.size > 1 }?.let {
                logger.log(
                    "DirScope <${dirLicensing.scope}>: more than one license found: " +
                            "${it.joinToString(" & ")}  dual licensed or multiple licenses",
                    Level.WARN,
                    pack.id,
                    phase = ProcessingPhase.POST
                )
            }
        }
    }

    /**
     * Decides if the source code is needed, e.g. for isLicenseText == true
     */
    override fun needsSourceCode(
        scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
        pack: Pack
    ): Boolean = scanDict[pack.id]?.any { entry ->
        entry.value.licenseTextEntries.any { lte -> lte.isLicenseText } ||
                OSCakeConfigParams.scopePatterns.any {
                    FileSystems.getDefault().getPathMatcher("glob:$it").matches(
                        if (OSCakeConfigParams.lowerCaseComparisonOfScopePatterns)
                            File(File(entry.value.path).name.lowercase()).toPath()
                        else
                            File(File(entry.value.path).name).toPath()
                    )
                }
    } ?: false

    /**
     * If no license is found for the project, a default one is created and filled with information provided by the
     * declaredLicenses (its origin is the package manager and not the scanner).
     */
    private fun prepareEntryForScopeDefault(pack: Pack, input: ReporterInput) {
        if (pack.declaredLicenses.size == 0) logger.log(
            "No declared license found for project/package!",
            Level.WARN,
            pack.id,
            phase = ProcessingPhase.POST
        )
        pack.declaredLicenses.forEach {
            val pathInArchive: String? = null
            DefaultLicense(it.toString(), FOUND_IN_FILE_SCOPE_DECLARED, pathInArchive).apply {
                pack.defaultLicensings.add(this)
                if (isInstancedLicense(input, it.toString())) {
                    logger.log(
                        "Declared license: <$it> is instanced license - no license text provided!",
                        Level.WARN,
                        pack.id,
                        phase = ProcessingPhase.POST
                    )
                } else {
                    logger.log(
                        "Declared license <$it> used for project/package",
                        Level.INFO,
                        pack.id,
                        phase = ProcessingPhase.POST
                    )
                }
            }
        }
    }
}
