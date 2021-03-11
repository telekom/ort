package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.reporter.reporters.createPathFlat
import org.ossreviewtoolkit.reporter.reporters.getLicensesFolderPrefix
import org.ossreviewtoolkit.reporter.reporters.getPathName
import java.io.File

internal class ModeREUSE(private val pack: Pack, private val scanDict: MutableMap<Identifier,
        MutableMap<String, FileInfoBlock>>) : ModeSelector() {

    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger("OSCakeReporter") }

    override fun fetchInfosFromScanDictionary(sourceCodeDir: String?, tmpDirectory: File
    ) {
        scanDict[pack.id]?.forEach { _, fib ->
            val isInLicensesFolder = fib.path.startsWith(getLicensesFolderPrefix(pack.packageRoot))
            if (fib.licenseTextEntries.any { it.isLicenseText && !isInLicensesFolder})
                logger.log ("Found a license text in \"${fib.path}\" - this is outside of the LICENSES folder" +
                        " - will be ignored!", Severity.ERROR)

            // Phase I: inspect ./LICENSES/*
            if (isInLicensesFolder) handleLicenses(fib, sourceCodeDir, tmpDirectory)
            // Phase II: handle files with ending  ".license" - should be binary files
            if (!isInLicensesFolder && fib.path.endsWith(".license")) handleBinaryFiles(fib)
            // Phase III: handle all other files
            if (!isInLicensesFolder && !fib.path.endsWith(".license")) handleDefaultFiles(fib)
            // Phase IV; handle Copyrights
            if (fib.copyrightTextEntries.size > 0) handleCopyrights(fib)
        }
    }

    private fun handleDefaultFiles(fib: FileInfoBlock) {
        FileLicensing(getPathName(pack, fib)).apply {
            fib.licenseTextEntries.filter { it.isLicenseNotice }.forEach {
                licenses.add(FileLicense(it.license))
            }
            if(licenses.size > 0) pack.fileLicensings.add(this)
        }
    }

    private fun handleBinaryFiles(fib: FileInfoBlock) {
        val fileNameWithoutExtension = File(fib.path).nameWithoutExtension
        // check if there is no licenseTextEntry for a file without this extension
        if (scanDict[pack.id]?.any { it.key == fileNameWithoutExtension } == true) {
            logger.log("File \"${fileNameWithoutExtension}\" shows license infos although \"${fib.path}\" " +
                        "also exists! --> Files ignored!", Severity.ERROR)
            return
        }
        FileLicensing(getPathName(pack, FileInfoBlock(fileNameWithoutExtension))).apply {
            fib.licenseTextEntries.filter { it.isLicenseNotice }.forEach {
                licenses.add(FileLicense(it.license))
            }
            if(licenses.size > 0) pack.fileLicensings.add(this)
        }
    }

    private fun handleLicenses(fib: FileInfoBlock, sourceCodeDir: String?, tmpDirectory: File) {
        // REUSE license files should only contain one single licenseTextEntry (by definition)
        fib.licenseTextEntries.filter { it.isLicenseText && fib.licenseTextEntries.size == 1 }.forEach {
            val pathFlat = createPathFlat(pack.id, fib.path)
            val sourcePath = sourceCodeDir + "/" + pack.id.toPath("/") + "/" + fib.path
            File(sourcePath).copyTo(File(tmpDirectory.path + "/" + pathFlat))

            FileLicensing(getPathName(pack, fib)).apply {
                this.licenses.add(FileLicense(it.license, pathFlat))
                pack.fileLicensings.add(this)
            }
            pack.reuseLicensings.add(ReuseLicense(it.license, fib.path,  pathFlat))
        }
    }

    private fun handleCopyrights(fib: FileInfoBlock) =
        (pack.fileLicensings.firstOrNull { it.scope == getPathName(pack, fib) } ?: FileLicensing(
            getPathName(pack, fib)
        ).apply { pack.fileLicensings.add(this) })
            .apply {
                fib.copyrightTextEntries.forEach {
                    copyrights.add(FileCopyright(it.matchedText!!))
                }
            }

    override fun postActivities() {
        // nothing to do
    }

}
