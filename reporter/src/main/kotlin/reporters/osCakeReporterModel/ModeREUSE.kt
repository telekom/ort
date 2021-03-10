package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.reporter.reporters.createPathFlat
import org.ossreviewtoolkit.reporter.reporters.getLicensesFolderPrefix
import org.ossreviewtoolkit.reporter.reporters.getPathName
import java.io.File

internal class ModeREUSE(private val pack: Pack, private val scanDict: MutableMap<Identifier,
        MutableMap<String, FileInfoBlock>>) : ModeSelector() {

    internal override fun fetchInfosFromScanDictionary(sourceCodeDir: String?, tmpDirectory: File
    ) {
       println(sourceCodeDir + "- " + tmpDirectory.name)
        scanDict[pack.id]?.forEach { _, fib ->
        //    println(fileName + " - " + fib.licenseTextEntries.size)
            // case 1: LICENSES/file
            if(fib.path.startsWith(getLicensesFolderPrefix(pack.packageRoot)))
                handleLicenses(fib, sourceCodeDir, tmpDirectory)
            // case 2: endsWith: ".license"
            // case 3: default

        }
    }

    private fun handleLicenses(fib: FileInfoBlock, sourceCodeDir: String?, tmpDirectory: File) {
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

}
