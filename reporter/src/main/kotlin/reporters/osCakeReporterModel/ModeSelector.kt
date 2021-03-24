/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import java.io.File

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.reporter.ReporterInput

/**
 * The [ModeSelector] implements a factory depending on the type of package and distinguishes between
 * REUSE-compliant and non-REUSE-compliant packages
 * */
internal abstract class ModeSelector {
    /**
     * The method processes the packages and fetches infos from the scanner output
     */
    internal abstract fun fetchInfosFromScanDictionary(sourceCodeDir: String?, tmpDirectory: File)

    /**
     * Defines program steps after terminating the [fetchInfosFromScanDictionary] method
     */
    internal abstract fun postActivities()

    /**
     * The [logger] is only initialized, if there is something to log.
     */
    internal val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(REPORTER_LOGGER) }

    companion object {
        /**
         * The [getMode] method returns an instance of [ModeREUSE] or [ModeDefault] depending on the type
         * of package
         */
        internal fun getMode(pack: Pack, scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
                             osCakeConfiguration: OSCakeConfiguration, reporterInput: ReporterInput): ModeSelector {
            return when (pack.reuseCompliant) {
                true -> ModeREUSE(pack, scanDict)
                else -> ModeDefault(pack, scanDict, osCakeConfiguration, reporterInput)
            }
        }
    }
}
