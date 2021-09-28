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

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.log

/**
 * The [ModeSelector] implements a factory depending on the type of package and distinguishes between
 * REUSE-compliant and non-REUSE-compliant packages
 * */
internal abstract class ModeSelector {
    /**
     * The method processes the packages and fetches infos from the scanner output
     */
    internal abstract fun fetchInfosFromScanDictionary(sourceCodeDir: String?, tmpDirectory: File,
                                                       provenance: Provenance)

    /**
     * Defines program steps after terminating the [fetchInfosFromScanDictionary] method
     */
    internal abstract fun postActivities()

    /**
     * Defines if the source code has to be downloaded
     */
    internal abstract fun needsSourceCode(scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
                                          pack: Pack): Boolean

    /**
     * The [logger] is only initialized, if there is something to log.
     */
    internal val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(REPORTER_LOGGER) }

    internal fun downloadSourcesWhenNeeded(pack: Pack, scanDict: MutableMap<Identifier, MutableMap<String,
            FileInfoBlock>>, /* reporterInput: ReporterInput, */ scannerPackageProvenance: Provenance) {
        if (!needsSourceCode(scanDict, pack)) return
        val pkg = pkgMap[pack.id]!!

        val downloadDir = File(osCakeConfig.sourceCodesDir)
        val downloaderConfig = DownloaderConfiguration()

        val provenanceHash = getHash(scannerPackageProvenance)

        @Suppress("SwallowedException")
        try {
            val downloadDirectory = downloadDir.resolve(pkg.id.toPath()).resolve(provenanceHash)
            // Check if package has already been downloaded
            if (downloadDirectory.exists()) {
                log.info { "No source code download necessary for Package: $(pkg!!.id.toPath())." }
                return
            }
            val downloadProvenance = Downloader(downloaderConfig).download(pkg, downloadDirectory)

            if (downloadProvenance != scannerPackageProvenance) {
                log.warn { "Mismatching provenance when creating missing source code for $(pkg!!.id.toPath())." }
            }
        } catch (ex: DownloadException) {
            log.error { "Error when downloading sources for Package: $(pkg!!.id.toPath())." }
        }
    }

    companion object {
        lateinit var pkgMap: MutableMap<Identifier, Package>
        lateinit var osCakeConfig: OSCakeConfiguration
        /**
         * The [getMode] method returns an instance of [ModeREUSE] or [ModeDefault] depending on the type
         * of package
         */
        internal fun getMode(pack: Pack, scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
                             osCakeConfiguration: OSCakeConfiguration, reporterInput: ReporterInput,
                             packageMap: MutableMap<Identifier, Package>): ModeSelector {
            pkgMap = packageMap
            osCakeConfig = osCakeConfiguration
            return when (pack.reuseCompliant) {
                true -> ModeREUSE(pack, scanDict)
                else -> ModeDefault(pack, scanDict, osCakeConfiguration, reporterInput)
            }
        }
    }
}
