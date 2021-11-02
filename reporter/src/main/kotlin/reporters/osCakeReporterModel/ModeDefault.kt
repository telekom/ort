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
import java.io.FileNotFoundException
import java.nio.file.FileSystems

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.reporter.ReporterInput

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
     * [osCakeConfiguration] contains configuration information
     */
    private val osCakeConfiguration: OSCakeConfiguration,
    /**
     * [reporterInput] provides information about the analyzed and scanned packages (from ORT)
     */
    private val reporterInput: ReporterInput) : ModeSelector() {

    /**
     * The method creates [FileLicensing]s for each file containing license information. Depending on the
     * scopeLevel (FILE, DIR, or DEFAULT) additional Licensings are added to the dirLicensings or defaultLicensings
     * lists. Information about copyrights is transferred from the fib (file info block) to the [pack].
     */
    override fun fetchInfosFromScanDictionary(sourceCodeDir: String?, tmpDirectory: File, provenance: Provenance) {
        val provHash = getHash(provenance)
        /*
         * Phase I: identify files on "dir" or "default"-scope containing license information:
         *          - copy the file to the archive file
         *          - create a fileLicensing entry
         * (Info: "default", "dir" scope depends on the matching of filenames against "scopePatterns" in oscake.conf)
         */
        @Suppress("SwallowedException")
        try {
            scanDict[pack.id]?.forEach { fileName, fib ->
                val scopeLevel = getScopeLevel(fileName, pack.packageRoot, osCakeConfiguration.scopePatterns)
                if ((scopeLevel == ScopeLevel.DIR || scopeLevel == ScopeLevel.DEFAULT) &&
                    fib.licenseTextEntries.size > 0) {
                    val pathFlat = createPathFlat(pack.id, fib.path)
                    File(sourceCodeDir + "/" + pack.id.toPath("/") + "/" + provHash + "/" + fib.path)
                        .copyTo(File(tmpDirectory.path + "/" + pathFlat))

                    FileLicensing(getPathName(pack, fib)).apply {
                        fileContentInArchive = pathFlat
                        pack.fileLicensings.add(this)
                    }
                }
            }
        } catch (ex: FileNotFoundException) {
            logger.log("File not found during creation of \"FileLicensings\"", Level.ERROR, pack.id,
                phase = ProcessingPhase.PROCESS)
            return
        }
        /*
         * Phase II: manage Default- and Dir-Scope entries and
         *           generate license texts (depending on license-category in license-classifications.yml) for each
         *           file which contains an "isLicenseText" entry
         */
        scanDict[pack.id]?.forEach { fileName, fib ->
            fib.licenseTextEntries /*.filter { it.isLicenseText }*/.forEach {
                val dedupFileName = handleDirDefaultEntriesAndLicenseTextsOnAllScopes(pack, sourceCodeDir, tmpDirectory,
                    fib, getScopeLevel(fileName, pack.packageRoot, osCakeConfiguration.scopePatterns), it, provHash)
                @Suppress("ComplexCondition")
                if ((it.isLicenseText && dedupFileName != null) || (!it.isLicenseText && dedupFileName == "")) {
                    addInfoToFileLicensings(pack, it, getPathName(pack, fib), dedupFileName)
                }
            }
        }
        /*
         * Phase III: copy archived files from scanner - and insert/update the fileLicensing entry
         *           (Info: files are archived from scanner, if the filename matches a pattern in ort.conf)
         */
        reporterInput.licenseInfoResolver.resolveLicenseFiles(pack.id).files.forEach {
            var path = it.path.replace("\\", "/")
            if (it.path.startsWith(pack.packageRoot) && pack.packageRoot != "") path = path.replaceFirst(
                pack.packageRoot, "").substring(1)

            val fl = pack.fileLicensings.firstOrNull { it.scope == path } ?: FileLicensing(path).apply {
                licenses.add(FileLicense(null))
                pack.fileLicensings.add(this)
            }

            if (fl.fileContentInArchive == null) {
                fl.fileContentInArchive = createPathFlat(pack.id, it.path) + "_archived"
                it.file.copyTo(File(tmpDirectory.path + "/" + fl.fileContentInArchive))
            }
        }
        /*
         * Phase IV: transfer Copyright text entries
         */
        scanDict[pack.id]?.forEach { _, fib ->
            if (fib.copyrightTextEntries.size > 0) {
                (pack.fileLicensings.firstOrNull { it.scope == getPathName(pack, fib) } ?:
                FileLicensing(getPathName(pack, fib)).apply { pack.fileLicensings.add(this) })
                    .apply {
                        fib.copyrightTextEntries.forEach {
                            copyrights.add(FileCopyright(it.matchedText!!))
                        }
                    }
            }
        }
    }

    /**
     * Adds a [FileLicensing], if it does not exist already.
     */
    private fun addInfoToFileLicensings(pack: Pack, licenseTextEntry: LicenseTextEntry, path: String,
                                        pathFlat: String): FileLicensing {
        val fileLicensing = pack.fileLicensings.firstOrNull { it.scope == path } ?: FileLicensing(path)

        if (fileLicensing.licenses.none { it.license == licenseTextEntry.license &&
                    it.startLine == licenseTextEntry.startLine }) {
            fileLicensing.licenses.add(FileLicense(licenseTextEntry.license,
                if (licenseTextEntry.isLicenseText) pathFlat else null, licenseTextEntry.startLine))
        }

        if (pack.fileLicensings.none { it.scope == path }) pack.fileLicensings.add(fileLicensing)

        return fileLicensing
    }

    /**
     *  generate and store license texts based on the info "isInstancedLicense" directly from source files
     *  in case of "dir" or "default"-scope the appropriate entries are inserted/updated.
     */
    private fun handleDirDefaultEntriesAndLicenseTextsOnAllScopes(
        pack: Pack,
        sourceCodeDir: String?,
        tmpDirectory: File,
        fib: FileInfoBlock,
        scopeLevel: ScopeLevel,
        lte: LicenseTextEntry,
        provHash: String
    ): String? {

        var genText: String? = null
        var file: File? = null

        try {
            if (lte.isLicenseText) {
                genText =
                    if (lte.isInstancedLicense) generateInstancedLicenseText(pack, fib, sourceCodeDir, lte, provHash)
                    else generateNonInstancedLicenseText(pack, fib, sourceCodeDir, lte, provHash)
                if (genText != null) file = File(
                    deduplicateFileName(
                        tmpDirectory.path + "/" +
                                createPathFlat(pack.id, fib.path, lte.license)
                    )
                )
            }
        } catch (ex: FileNotFoundException) {
            logger.log("File not found: ${ex.message}  while generating license texts!", Level.ERROR, pack.id,
            phase = ProcessingPhase.PROCESS)
            return ""
        }

        when (scopeLevel) {
            ScopeLevel.DEFAULT -> {
                val fibPathWithoutPackage = getPathWithoutPackageRoot(pack, fib.path)
                DefaultLicense(
                    lte.license, fibPathWithoutPackage, file?.name, false
                ).apply {
                    if (pack.defaultLicensings.none { it.license == lte.license &&
                                it.path == fibPathWithoutPackage }) {
                        pack.defaultLicensings.add(this)
                        if (lte.isLicenseText) file?.writeText(genText!!)
                    } else {
                        val lic = pack.defaultLicensings.first { it.license == lte.license &&
                                it.path == fibPathWithoutPackage }
                        val ll = if (lic.license == "NOASSERTION") Level.INFO else Level.DEBUG
                        logger.log(
                            "multiple equal licenses <${lte.license}> in the same file found: ${fib.path}" +
                                    " - ignored!", ll, pack.id, fib.path, lic.license, ScopeLevel.DEFAULT, ProcessingPhase.PROCESS)
                    }
                }
            }
            ScopeLevel.DIR -> {
                val dirScope = getDirScopePath(pack, fib.path)
                val fibPathWithoutPackage = getPathWithoutPackageRoot(pack, fib.path)
                val dirLicensing = pack.dirLicensings.firstOrNull { it.scope == dirScope } ?: DirLicensing(
                    dirScope).apply {
                    pack.dirLicensings.add(this)
                }
                if (dirLicensing.licenses.none { it.license == lte.license &&
                            it.path == fibPathWithoutPackage }) {
                    dirLicensing.licenses.add(DirLicense(lte.license!!,
                        file?.name, fibPathWithoutPackage))
                    if (lte.isLicenseText) file?.writeText(genText!!)
                } else {
                    val lic = dirLicensing.licenses.first { it.license == lte.license &&
                            it.path == fibPathWithoutPackage }
                    val ll = if (lic.license == "NOASSERTION") Level.INFO else Level.DEBUG
                    logger.log(
                        "multiple equal licenses <${lte.license}> in the same file found: ${fib.path}" +
                                " - ignored!", ll, pack.id, fib.path, lic.license, ScopeLevel.DIR, ProcessingPhase.PROCESS)
                }
            }
            ScopeLevel.FILE -> if (lte.isLicenseText) file?.writeText(genText!!)
        }
        if (lte.isLicenseText) return file?.name
        return ""
    }

    /**
     * The method calculates the first and the last line of the requested license text and returns it (an instanced
     * license must begin with a copyright statement).
     */
    private fun generateInstancedLicenseText(pack: Pack, fileInfoBlock: FileInfoBlock, sourceCodeDir: String?,
                                             licenseTextEntry: LicenseTextEntry, provHash: String): String? {

        val fileName = sourceCodeDir + "/" + pack.id.toPath("/") + "/" + provHash + "/" + fileInfoBlock.path
        val copyrightStartLine = getCopyrightStartline(fileInfoBlock, licenseTextEntry, fileName)
        if (copyrightStartLine == null) {
            logger.log("No Copyright-Startline found in ${fileInfoBlock.path}", Level.ERROR, pack.id,
                fileInfoBlock.path, phase = ProcessingPhase.PROCESS)
            return null
        }
        if (copyrightStartLine > licenseTextEntry.endLine) {
            logger.log("Line markers $copyrightStartLine : ${licenseTextEntry.endLine} not valid in: " +
                    "${fileInfoBlock.path}", Level.ERROR, pack.id, fileInfoBlock.path, phase = ProcessingPhase.PROCESS)
            return null
        }
        return getLinesFromFile(fileName, copyrightStartLine, licenseTextEntry.endLine)
    }

    private fun getLinesFromFile(path: String, startLine: Int, endLine: Int): String =
        File(path).readLines()
            .slice(startLine - 1 until endLine)
            .joinToString(separator = System.lineSeparator()) { it.getRidOfCommentSymbols() }

    /**
     * The copyright statement must be found above of the license text [licenseTextEntry]; therefore, search for the
     * smallest startline of a copyright entry without crossing another license text
     */
    private fun getCopyrightStartline(fileInfoBlock: FileInfoBlock, licenseTextEntry: LicenseTextEntry, fileName: String): Int? {
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
        line?: run {
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
        the license text) does not work. Solution: find copyright (start and endline) inside of a license text entry.
         */
        line?: lastLicenseTextEntry?.run {
            if (completeList.filterIsInstance<CopyrightTextEntry>().any {
                it.startLine >= lastLicenseTextEntry.startLine && it.endLine <= lastLicenseTextEntry.endLine })
                line = lastLicenseTextEntry.startLine
        }

        return line
    }

    /**
     * The method provides the license text for non-instanced licenses directly from the source file.
     */
    private fun generateNonInstancedLicenseText(pack: Pack, fileInfoBlock: FileInfoBlock, sourceCodeDir: String?,
                                                licenseTextEntry: LicenseTextEntry, provHash: String): String? {
        val textBlockList = fileInfoBlock.licenseTextEntries.filter { it.isLicenseText &&
                it.license == licenseTextEntry.license }.toMutableList<TextEntry>()
        if (textBlockList.size > 0) {
            textBlockList.sortBy { it.startLine }
            val fileName = sourceCodeDir + "/" + pack.id.toPath("/") + "/" + provHash + "/" + fileInfoBlock.path
            return getLinesFromFile(fileName, textBlockList[0].startLine, textBlockList[textBlockList.size - 1].endLine)
        }
        return null
    }

    override fun postActivities() {
        if (pack.defaultLicensings.size == 0) prepareEntryForScopeDefault(pack, reporterInput)
    }

    /**
     * decides if the source code is needed, e.g. for isLicenseText == true
     */
    override fun needsSourceCode(
        scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
        pack: Pack
    ): Boolean = scanDict[pack.id]?.any { entry ->
                entry.value.licenseTextEntries.any { lte -> lte.isLicenseText } ||
                osCakeConfiguration.scopePatterns.any {
                    FileSystems.getDefault().getPathMatcher("glob:$it").
                        matches(File(File(entry.value.path).name).toPath())
                }
        } ?: false


    /**
     * If no license is found for the project, a default one is created and filled with information provided by the
     * declaredLicenses (its origin is the package manager and not the scanner).
     */
    private fun prepareEntryForScopeDefault(pack: Pack, input: ReporterInput) {
        if (pack.declaredLicenses.size == 0) logger.log("No declared license found for project/package!",
            Level.WARN, pack.id, phase = ProcessingPhase.POST)
        pack.declaredLicenses.forEach {
            val pathInArchive: String? = null
            DefaultLicense(it.toString(), FOUND_IN_FILE_SCOPE_DECLARED, pathInArchive).apply {
                pack.defaultLicensings.add(this)
                if (isInstancedLicense(input, it.toString())) logger.log(
                    "Declared license: <$it> is instanced license - no license text provided!",
                    Level.WARN, pack.id, phase = ProcessingPhase.POST)
                else
                    logger.log("Declared license <$it> used for project/package", Level.INFO,
                        pack.id, phase = ProcessingPhase.POST)
            }
        }
    }
}
