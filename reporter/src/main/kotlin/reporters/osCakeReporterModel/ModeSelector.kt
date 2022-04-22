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

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.reporter.ReporterInput

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
    internal abstract fun postActivities(tmpDirectory: File)

    /**
     * Defines if the source code has to be downloaded
     */
    internal abstract fun needsSourceCode(scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
                                          pack: Pack): Boolean

    /**
     * The [logger] is only initialized, if there is something to log.
     */
    internal val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(REPORTER_LOGGER) }

    /**
     * if the source code for the package is not yet in side of the sourcecode cache, and it is needed (e.g.
     * for instanced licenses) it is copied into the sourcecode folder (defined by the package-id and
     * the provenance, e.g. ./Maven/joda-time/joda-time/2.10.8/39b1a3cc0a54d8b34737520bc066d2baee2fe2a4 )
     */
    internal fun downloadSourcesWhenNeeded(pack: Pack, scanDict: MutableMap<Identifier, MutableMap<String,
            FileInfoBlock>>, scannerPackageProvenance: Provenance) {
        if (!needsSourceCode(scanDict, pack)) return
        val pkg = pkgMap[pack.id]!!

        val downloadDir = File(OSCakeConfigParams.sourceCodesDir!!)
        val downloaderConfig = DownloaderConfiguration()

        val provenanceHash = getHash(scannerPackageProvenance)

        @Suppress("SwallowedException")
        try {
            val downloadDirectory = downloadDir.resolve(pkg.id.toPath()).resolve(provenanceHash)
            // Check if package has already been downloaded
            if (downloadDirectory.exists()) {
                logger.log("No source code download necessary for Package: ${pkg.id}.", Level.DEBUG,
                phase = ProcessingPhase.DOWNLOAD)
                return
            } else logger.log("Source code for ${pkg.id} is being downloaded.", Level.DEBUG)
            val downloadProvenance = Downloader(downloaderConfig).download(pkg, downloadDirectory)
            logger.log("Source code download for ${pkg.id} completed.", Level.DEBUG,
                phase = ProcessingPhase.DOWNLOAD)

            if (downloadProvenance != scannerPackageProvenance) {
                logger.log("Mismatching provenance when creating missing source code for ${pkg.id}.",
                    Level.WARN, pkg.id, phase = ProcessingPhase.DOWNLOAD)
            }
        } catch (ex: DownloadException) {
            logger.log("Error when downloading sources.", Level.WARN, pkg.id, phase = ProcessingPhase.DOWNLOAD)
        }
    }

    companion object {
        lateinit var pkgMap: MutableMap<Identifier, Package>
        /**
         * The [getMode] method returns an instance of [ModeREUSE] or [ModeDefault] depending on the type
         * of package
         */
        internal fun getMode(pack: Pack, scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
                             reporterInput: ReporterInput,
                             packageMap: MutableMap<Identifier, Package>): ModeSelector {
            pkgMap = packageMap
            return when (pack.reuseCompliant) {
                true -> ModeREUSE(pack, scanDict)
                else -> ModeDefault(pack, scanDict, reporterInput)
            }
        }
    }
}
