package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.reporter.ReporterInput
import java.io.File

abstract class ModeSelector {
    internal abstract fun fetchInfosFromScanDictionary(sourceCodeDir: String?, tmpDirectory: File)
    internal abstract fun postActivities()

    internal val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(REPORTER_LOGGER) }

    companion object {
        internal fun getMode(pack: Pack, scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
                             osCakeConfiguration: OSCakeConfiguration, reporterInput: ReporterInput): ModeSelector {
            return when (pack.reuseCompliant) {
                true -> ModeREUSE(pack, scanDict)
                else -> ModeDefault(pack, scanDict, osCakeConfiguration, reporterInput)
            }
        }
    }
}
