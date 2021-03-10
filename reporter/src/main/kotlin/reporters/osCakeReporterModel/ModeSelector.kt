package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.ossreviewtoolkit.model.Identifier
import java.io.File

abstract class ModeSelector {
    internal abstract fun fetchInfosFromScanDictionary(sourceCodeDir: String?, tmpDirectory: File)

    companion object {
        internal fun getMode(pack: Pack, scanDict: MutableMap<Identifier,
                MutableMap<String, FileInfoBlock>>): ModeSelector {
            return when (pack.reuseCompliant) {
                true -> ModeREUSE(pack, scanDict)
                //else -> ModeDefault()
                else -> throw Exception("Invalid document type")
            }
        }
    }
}
