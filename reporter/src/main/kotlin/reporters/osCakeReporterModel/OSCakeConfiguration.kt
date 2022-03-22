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
data class PackageRestrictions(
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
data class PackageInclusions(
    /**
     * shows if the mechanism for package inclusions is enabled, only works if the commandline parameter for
     * dependency-granularity is set
     */
    val enabled: Boolean?,
    /**
     * contains a list of packages in IVY-notation, which has to be included - all others will be ignored
     */
    val forceIncludePackages: MutableList<String>?
)

/**
 * Part of the [OSCakeConfiguration]
 */
data class IncludeIssues(
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
data class OSCakeConfiguration(
    /**
     *  [scopePatterns] contains a list of glob patterns which are used to determine the corresponding [ScopeLevel]
     *  of licenses.
     */
    val scopePatterns: List<String> = mutableListOf(),
    /**
     *  [copyrightScopePatterns] contains a list of glob patterns which are used to determine the
     *  corresponding [ScopeLevel] for copyrights - is an extension to scopePatterns
     */
    val copyrightScopePatterns: List<String> = mutableListOf(),
    /**
     *  [scopeIgnorePatterns] contains a list of glob patterns which excludes files for determination of the
     *  corresponding [ScopeLevel] for licenses and copyrights
     */
    val scopeIgnorePatterns: List<String> = mutableListOf(),
    /**
     * [sourceCodesDir] folders where to find or save the source code.
     */
    val sourceCodesDir: String? = null,
    /**
     * [ortScanResultsDir] folder where ORT stores its native scan results.
     */
    val ortScanResultsDir: String? = null,
    /**
     * [packageRestrictions] include only the named packages - defined as [Identifier](s) when processing.
     */
    val packageRestrictions: PackageRestrictions? = null,
    /**
     * [packageInclusions] include the named packages despite the fact that they would be excluded due to the
     * commandline option: dependency-granularity.
     */
    val packageInclusions: PackageInclusions? = null,
    /**
     * defines if issues should be reported in oscc output
     */
    val includeIssues: IncludeIssues? = null,
    /**
     * defines if the log messages should be enriched with json paths concerning the oscc file
     */
    val includeJsonPathInLogfile4ErrorsAndWarnings: Boolean? = false,
    /**
     * defines if license findings for license "NOASSERTION" is ignored
     */
    val ignoreNOASSERTION: Boolean? = false,
    /**
     * defines if license findings starting with "LicenseRef-scancode-unknown" are ignored
     */
    val ignoreLicenseRefScancodeUnknown: Boolean? = false,
    /**
     * remove json sections from oscc-file
     */
    val hideSections: List<String>? = emptyList(),
    /**
     * in order to get the same results for Windows and Unix systems when identifying the Default- or Dir-scope
     */
    val lowerCaseComparisonOfScopePatterns: Boolean? = true,
    /**
     * defines if license findings starting with "LicenseRef" are ignored
     */
    val ignoreLicenseRef: Boolean? = false,
    /**
     * defines the boundary for license scores - every license finding below this threshold will be logged as warning
     */
    val licenseScoreThreshold: Int? = 0
    ) {
    companion object {
        private lateinit var osCakeConfig: OSCakeConfiguration
        private val commandLineParams: MutableMap<String, String> = mutableMapOf()
        private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(REPORTER_LOGGER) }
        lateinit var params: OSCakeConfigParams
        lateinit var osCakeConfigInfo: OSCakeConfigInfo

        fun isParamsInitialized() = ::params.isInitialized

        /**
         * [setConfigParams] contains logical checks and combinations of configuration entries set in
         * osCakeConfig
         */
        internal fun setConfigParams(options: Map<String, String>) {
            osCakeConfig = getOSCakeConfiguration(options["configFile"]!!)
            require(isValidFolder(osCakeConfig.sourceCodesDir)) {
                "Invalid or missing config entry found for \"sourceCodesDir\" in oscake.conf"
            }
            val ortScanResultsDir = osCakeConfig.ortScanResultsDir
            // init of params necessary, because the logger already needs this information
            params = OSCakeConfigParams(osCakeConfig.includeJsonPathInLogfile4ErrorsAndWarnings ?: false)

            val oscakeScanResultsDir: String? = null
            params.dependencyGranularity = if (options["--dependency-granularity"]?.
                toIntOrNull() != null) options["--dependency-granularity"]!!.toInt() else Int.MAX_VALUE

            val onlyIncludePackagesMap: MutableMap<Identifier, Boolean> = mutableMapOf()
            osCakeConfig.packageRestrictions?.let { packageRestriction ->
                if (packageRestriction.enabled != null && packageRestriction.enabled) {
                    var infoStr = "The output is restricted - set in configuration - to the following package(s):\n"
                    packageRestriction.onlyIncludePackages?.forEach {
                        val id = Identifier(it)
                        onlyIncludePackagesMap[id] = false
                        infoStr += "   $id\n"
                    }
                    logger.log(infoStr, Level.INFO, phase = ProcessingPhase.CONFIG)
                    params.dependencyGranularity = Int.MAX_VALUE
                    logger.log("commandline parameter \"dependency-granularity\" is overruled due to " +
                            "packageRestrictions", Level.INFO, phase = ProcessingPhase.CONFIG)
                }
            }

            var issueLevel: Int
            osCakeConfig.includeIssues?.enabled.let {
                issueLevel = osCakeConfig.includeIssues?.level ?: -1
                if (issueLevel > 2) issueLevel = 2
                if (it == false) issueLevel = -1
            }

            params.ortScanResultsDir = ortScanResultsDir
            params.oscakeScanResultsDir = oscakeScanResultsDir
            params.onlyIncludePackages = onlyIncludePackagesMap

            val forceIncludePackagesMap: MutableMap<Identifier, Boolean> = mutableMapOf()
            osCakeConfig.packageInclusions?.let { packageInclusion ->
                 if (params.dependencyGranularity != Int.MAX_VALUE && packageInclusion.enabled != null &&
                     packageInclusion.enabled) {
                     var infoStr = "The output is extended (set in configuration and despite of the commandline " +
                             "parameter: \"dependency-granularity\" ) with the following package(s):\n"
                     packageInclusion.forceIncludePackages?.forEach {
                         val id = Identifier(it)
                         forceIncludePackagesMap[id] = false
                         infoStr += "   $id\n"
                     }
                     logger.log(infoStr, Level.INFO, phase = ProcessingPhase.CONFIG)
                 }
             }
            params.forceIncludePackages = forceIncludePackagesMap

            params.issuesLevel = issueLevel
            params.sourceCodesDir = osCakeConfig.sourceCodesDir

            params.lowerCaseComparisonOfScopePatterns = osCakeConfig.lowerCaseComparisonOfScopePatterns ?: true
            params.scopePatterns = osCakeConfig.scopePatterns
            params.copyrightScopePatterns = (osCakeConfig.copyrightScopePatterns +
                    osCakeConfig.scopePatterns).toList()
            params.scopeIgnorePatterns = osCakeConfig.scopeIgnorePatterns
            if (params.lowerCaseComparisonOfScopePatterns) {
                params.scopePatterns = params.scopePatterns.map { it.lowercase() }.distinct().toList()
                params.copyrightScopePatterns = params.copyrightScopePatterns.map { it.lowercase() }.distinct().toList()
                params.scopeIgnorePatterns = params.scopeIgnorePatterns.map { it.lowercase() }.distinct().toList()
            }

            params.ignoreNOASSERTION = osCakeConfig.ignoreNOASSERTION ?: false
            params.ignoreLicenseRefScancodeUnknown = osCakeConfig.ignoreLicenseRefScancodeUnknown ?: false
            params.ignoreLicenseRef = osCakeConfig.ignoreLicenseRef ?: false
            params.hideSections = osCakeConfig.hideSections ?: emptyList()
            params.licenseScoreThreshold = osCakeConfig.licenseScoreThreshold?: 0

            options.forEach {
                commandLineParams[it.key] = it.value
            }
            osCakeConfigInfo = OSCakeConfigInfo(commandLineParams, osCakeConfig)
        }
        /**
         * fetches the options which were passed via "-O OSCake=...=..."
         */
        private fun getOSCakeConfiguration(fileName: String): OSCakeConfiguration {
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
