package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.reporter.ReporterInput

import java.io.File

internal class ModeDefault(private val pack: Pack, private val scanDict:
       MutableMap<Identifier, MutableMap<String, FileInfoBlock>>, private val osCakeConfiguration: OSCakeConfiguration,
       private val reporterInput: ReporterInput) : ModeSelector() {

    override fun postActivities() {
        if (pack.defaultLicensings.size == 0) prepareEntryForScopeDefault(pack, reporterInput)
    }

    override fun fetchInfosFromScanDictionary(sourceCodeDir: String?, tmpDirectory: File) {
        /*
         * Phase I: identify files on "dir" or "default"-scope containing license information:
         *          - copy the file to the archive file
         *          - create a fileLicensing entry
         * (Info: "default", "dir" scope depends on the matching of filenames against "scopePatterns" in oscake.conf)
         */
        scanDict[pack.id]?.forEach { fileName, fib ->
            val scopeLevel = getScopeLevel(fileName, pack.packageRoot, osCakeConfiguration.scopePatterns)
            if ((scopeLevel == ScopeLevel.DIR || scopeLevel == ScopeLevel.DEFAULT) && fib.licenseTextEntries.size > 0) {
                val pathFlat = createPathFlat(pack.id, fib.path)
                File(sourceCodeDir + "/" + pack.id.toPath("/") + "/" + fib.path).copyTo(
                    File(tmpDirectory.path + "/" + pathFlat))

                FileLicensing(getPathName(pack, fib)).apply {
                    fileContentInArchive = pathFlat
                    pack.fileLicensings.add(this)
                }
            }
        }
        /*
         * Phase II: manage Default- and Dir-Scope entries and
         *           generate license texts (depending on license-category in license-classifications.yml) for each
         *           file which contains an "isLicenseText" entry
         */
        scanDict[pack.id]?.forEach { fileName, fib ->
            fib.licenseTextEntries /*.filter { it.isLicenseText }*/.forEach {
                val dedupFileName = handleDirDefaultEntriesAndLicenseTextsOnAllScopes(pack, sourceCodeDir, tmpDirectory,
                    fib, getScopeLevel(fileName, pack.packageRoot, osCakeConfiguration.scopePatterns), it)
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
     *  in case of "dir" or "default"-scope the appropriate entries are inserted/updated
     */
    private fun handleDirDefaultEntriesAndLicenseTextsOnAllScopes(
        pack: Pack,
        sourceCodeDir: String?,
        tmpDirectory: File,
        fib: FileInfoBlock,
        scopeLevel: ScopeLevel,
        lte: LicenseTextEntry,
    ): String? {

        var genText: String? = null
        var file: File? = null

        if (lte.isLicenseText) {
            genText = if (lte.isInstancedLicense) generateInstancedLicenseText(pack, fib, sourceCodeDir, lte)
            else generateNonInstancedLicenseText(pack, fib, sourceCodeDir, lte)
            if (genText != null) file = File(
                deduplicateFileName(tmpDirectory.path + "/" +
                    createPathFlat(pack.id, fib.path, lte.license))
            )
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
                        logger.log(
                            "multiple equal licenses <${lte.license}> in the same file found: ${fib.path}" +
                                    " - ignored!", Level.INFO, pack.id, fib.path)
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
                    logger.log("multiple equal licenses <${lte.license}> in the same file " +
                            "found: ${fib.path} - ignored!", Level.INFO, pack.id, fib.path)
                }
            }
            ScopeLevel.FILE -> if (lte.isLicenseText) file?.writeText(genText!!)
        }
        if (lte.isLicenseText) return file?.name
        return ""
    }

    private fun generateInstancedLicenseText(pack: Pack, fileInfoBlock: FileInfoBlock, sourceCodeDir: String?,
                                             licenseTextEntry: LicenseTextEntry): String? {

        val copyrightStartLine = getCopyrightStartline(fileInfoBlock, licenseTextEntry)
        if (copyrightStartLine == null) {
            logger.log("No Copyright-Startline found in ${fileInfoBlock.path}", Level.ERROR, pack.id,
                fileInfoBlock.path)
            return null
        }
        if (copyrightStartLine > licenseTextEntry.endLine) {
            logger.log("Line markers $copyrightStartLine : ${licenseTextEntry.endLine} not valid in: " +
                    "${fileInfoBlock.path}", Level.ERROR, pack.id, fileInfoBlock.path)
            return null
        }
        val fileName = sourceCodeDir + "/" + pack.id.toPath("/") + "/" + fileInfoBlock.path
        return getLinesFromFile(fileName, copyrightStartLine, licenseTextEntry.endLine)
    }

    private fun getLinesFromFile(path: String, startLine: Int, endLine: Int): String =
        File(path).readLines()
            .slice(startLine - 1..endLine - 1)
            .map { Regex("""^( \* *)""").replace(it, "") } // removes trailing comment symbols
            .joinToString(separator = System.lineSeparator())

    /**
     * The copyright statement must be found above of the license text [licenseTextEntry]; therefore, search for the
     * smallest startline of a copyright entry without crossing another license text
     */
    private fun getCopyrightStartline(fileInfoBlock: FileInfoBlock, licenseTextEntry: LicenseTextEntry): Int? {
        val completeList = fileInfoBlock.licenseTextEntries.filter { it.isLicenseText }.toMutableList<TextEntry>()
        completeList.addAll(fileInfoBlock.copyrightTextEntries)
        completeList.sortByDescending { it.startLine }
        var found = false
        var line: Int? = null

        loop@ for (lte in completeList) {
            if (found && lte is CopyrightTextEntry) line = lte.startLine
            if (found && lte is LicenseTextEntry) break@loop
            if (lte == licenseTextEntry) found = true
        }
        return line
    }

    private fun generateNonInstancedLicenseText(pack: Pack, fileInfoBlock: FileInfoBlock, sourceCodeDir: String?,
                                                licenseTextEntry: LicenseTextEntry): String? {
        val textBlockList = fileInfoBlock.licenseTextEntries.filter { it.isLicenseText &&
                it.license == licenseTextEntry.license }.toMutableList<TextEntry>()
        if (textBlockList.size > 0) {
            textBlockList.sortBy { it.startLine }
            val fileName = sourceCodeDir + "/" + pack.id.toPath("/") + "/" + fileInfoBlock.path
            return getLinesFromFile(fileName, textBlockList[0].startLine, textBlockList[textBlockList.size - 1].endLine)
        }
        return null
    }

    private fun prepareEntryForScopeDefault(pack: Pack, input: ReporterInput) {
        if (pack.declaredLicenses.size == 0) logger.log("No declared license found for project/package: " +
                pack.id, Level.WARN, pack.id)
        pack.declaredLicenses.forEach {
            val pathInArchive: String? = null
            if (isInstancedLicense(input, it.toString())) logger.log(
                "Declared license: <$it> is instanced license - no license text provided!: " +
                        pack.id, Level.ERROR, pack.id)
            DefaultLicense(it.toString(), FOUND_IN_FILE_SCOPE_DECLARED, pathInArchive).apply {
                pack.defaultLicensings.add(this)
            }
        }
        logger.log("declared license used for project/package: " + pack.id, Level.INFO, pack.id)
    }
}
