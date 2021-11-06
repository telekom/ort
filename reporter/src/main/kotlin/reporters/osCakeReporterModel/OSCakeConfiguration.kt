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

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.fp.getOrElse

import java.io.File

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier


/**
 * Part of the [OSCakeConfiguration]
 */
internal data class PackageRestrictions(
    /**
     * shows if the mechanism for package restrictions is enabled
     */
    val enabled: Boolean?,
    /**
     * contains a list of packages in IVY-notation, which has to be included - all others will be ignored
     */
    val onlyIncludePackages: MutableList<String>?
)

/**
 * Part of the [OSCakeConfiguration]
 */
internal data class IncludeIssues(
    /**
     * shows if the mechanism for including issues is enabled
     */
    val enabled: Boolean?,
    /**
     * contains a list of packages in IVY-notation, which has to be included - all others will be ignored
     */
    val level: Int? // 0..ERROR, 1..WARN + ERROR, 2..INFO + WARN + ERROR
)

/**
Wrapper class for the [OSCakeConfiguration] class - reads the file passed by option "OSCake=configFile=...:"
 */
internal data class OSCakeConfiguration(
    /**
     *  [scopePatterns] contains a list of glob patterns which are used to determine the corresponding [ScopeLevel].
     */
    val scopePatterns: List<String> = mutableListOf(),
    /**
     * [curations] contains information about the folders where the curation files can be found.
     */
    val curations: Map<String, String>? = null,
    /**
     * [sourceCodesDir] folders where to find or save the source code.
     */
    val sourceCodesDir: String? = null,
    /**
     * [scanResultsCache] holds information on how to handle scan-results.
     */
    val scanResultsCache: Map<String, String>? = null,
    /**
     * [ortScanResultsDir] folder where ORT stores its native scan results.
     */
    val ortScanResultsDir: String? = null,
    /**
     * [packageRestrictions] include only the named packages - defined as [Identifier](s) when processing.
     */
    val packageRestrictions: PackageRestrictions? = null,
    /**
     * defines if issues should be reported in oscc output
     */
    val includeIssues: IncludeIssues? = null,
    /**
     * defines if the log messages should be enriched with json paths concerning the oscc file
     */
    val includeJsonPathInLogfile4ErrorsAndWarnings: Boolean? = false

) {
    companion object {
        private lateinit var osCakeConfiguration: OSCakeConfiguration
        private val commandLineParams: MutableMap<String,String> = mutableMapOf()
        private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(REPORTER_LOGGER) }
        lateinit var params: OSCakeConfigParams
        lateinit var osCakeConfigInfo: OSCakeConfigInfo

        /**
         * [setConfigParams] contains logical checks and combinations of configuration entries set in osCakeConfiguration
         */
        internal fun setConfigParams(options: Map<String, String>) {
            osCakeConfiguration = getOSCakeConfiguration(options["configFile"]!!)
            commandLineParams["configFile"] = options["configFile"]!!
            if (osCakeConfiguration.curations?.get("enabled").toBoolean()) {
                require(isValidFolder(osCakeConfiguration.curations?.get("fileStore"))) {
                    "Invalid or missing config entry found for \"curations.filestore\" in oscake.conf"
                }
                require(isValidFolder(osCakeConfiguration.curations?.get("directory"))) {
                    "Invalid or missing config entry found for \"curations.directory\" in oscake.conf"
                }
            }
            require(isValidFolder(osCakeConfiguration.sourceCodesDir)) {
                "Invalid or missing config entry found for \"sourceCodesDir\" in oscake.conf"
            }
            val ortScanResultsDir = osCakeConfiguration.ortScanResultsDir
            // init of params necessary, because the logger already needs this information
            params = OSCakeConfigParams(osCakeConfiguration.includeJsonPathInLogfile4ErrorsAndWarnings?:false)

            var scanResultsCacheEnabled = false
            var oscakeScanResultsDir: String? = null
            val deleteOrtNativeScanResults = options.containsKey("--deleteOrtNativeScanResults")
            if (deleteOrtNativeScanResults)
                commandLineParams["deleteOrtNativeScanResults"] = true.toString()
            if (osCakeConfiguration.scanResultsCache?.get("enabled").toBoolean()) {
                scanResultsCacheEnabled = true
                require(isValidFolder(osCakeConfiguration.scanResultsCache?.getOrDefault("directory", ""))) {
                    "scanResultsCache in oscake.conf is enabled, but 'scanResultsCache.directory' is not a valid folder " +
                            "or 'scanResultsCache.directory' is missing in config!"
                }
                oscakeScanResultsDir = osCakeConfiguration.scanResultsCache?.getOrDefault("directory", null)
            } else {
                require(isValidFolder(ortScanResultsDir)) { "Conf-value for 'nativeScanResultsDir' is not a valid folder!" }
                if (deleteOrtNativeScanResults) {
                    logger.log(
                        "Option \"--deleteOrtNativeScanResults\" ignored, because \"scanResultsCache\" in " +
                                "oscake.conf does not exist or is not enabled!", Level.INFO, phase = ProcessingPhase.CONFIG
                    )
                }
            }
            val onlyIncludePackagesMap: MutableMap<Identifier, Boolean> = mutableMapOf()
            osCakeConfiguration.packageRestrictions?.let { packageRestriction ->
                if (packageRestriction.enabled != null && packageRestriction.enabled) {
                    var infoStr = "The output is restricted - set in configuration - to the following package(s):\n"
                    packageRestriction.onlyIncludePackages?.forEach {
                        val id = Identifier(it)
                        onlyIncludePackagesMap[id] = false
                        infoStr += "   $id\n"
                    }
                    logger.log(infoStr, Level.INFO, phase = ProcessingPhase.CONFIG)
                }
            }
            var issueLevel: Int
            osCakeConfiguration.includeIssues?.enabled.let {
                issueLevel = osCakeConfiguration.includeIssues?.level?:-1
                if (issueLevel > 2) issueLevel = 2
            }

            params.ortScanResultsDir = ortScanResultsDir
            params.scanResultsCacheEnabled = scanResultsCacheEnabled
            params.oscakeScanResultsDir = oscakeScanResultsDir
            params.onlyIncludePackages = onlyIncludePackagesMap
            params.dependencyGranularity = if (options["dependency-granularity"]?.
                toIntOrNull() != null) options["dependency-granularity"]!!.toInt() else Int.MAX_VALUE
            if (params.dependencyGranularity != Int.MAX_VALUE) commandLineParams["dependency-granularity"] =
                params.dependencyGranularity.toString()
            params.deleteOrtNativeScanResults = deleteOrtNativeScanResults
            params.issuesLevel = issueLevel
            params.sourceCodesDir = osCakeConfiguration.sourceCodesDir
            params.scopePatterns = osCakeConfiguration.scopePatterns
            params.curationsEnabled = osCakeConfiguration.curations?.get("enabled").toBoolean()
            if (params.curationsEnabled) {
                require(isValidFolder(osCakeConfiguration.curations?.getOrDefault("directory", ""))) {
                    "curations in oscake.conf are enabled, but 'curations.directory' is not a valid folder!"
                }
                require(isValidFolder(osCakeConfiguration.curations?.getOrDefault("fileStore", ""))) {
                    "curations in oscake.conf are enabled, but 'curations.fileStore' is not a valid folder!"
                }
                params.curationsDirectory = osCakeConfiguration.curations!!.getOrDefault("directory", "")
                params.curationsFileStore = osCakeConfiguration.curations!!.getOrDefault("fileStore", "")
            }
            osCakeConfigInfo = OSCakeConfigInfo(OSCakeConfiguration.commandLineParams, OSCakeConfiguration.osCakeConfiguration)
        }
        /**
         * fetches the options which were passed via "-O OSCake=...=..."
         */
        internal fun getOSCakeConfiguration(fileName: String): OSCakeConfiguration {
            val config = ConfigLoader.Builder()
                .addSource(PropertySource.file(File(fileName)))
                .build()
                .loadConfig<OSCakeWrapper>()

            return config.map { it.OSCake }.getOrElse { failure ->
                throw IllegalArgumentException(
                    "Failed to load configuration from ${failure.description()}")
            }
        }
        /**
         * check if the given folder name is an existing directory
         */
        private fun isValidFolder(dir: String?): Boolean =
            !(dir == null || !File(dir).exists() || !File(dir).isDirectory)

    }
}
