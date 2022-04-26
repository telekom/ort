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

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.fp.getOrElse

import java.io.File

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
    val licenseScoreThreshold: Int? = 0,
    /**
     * contains a mapping between scope names (may contain a regex expression) and its distribution type
     * the scope names may be prefixed by the name of a package manager
     */
    val distributionMap: MutableMap<String, String>? = mutableMapOf(),
    /**
     * if set tor true, the oscc file will be pretty printed (json)
     */
    val prettyPrint: Boolean = false
    ) {
    /**
     * The [OSCakeConfiguration] is read by the jackson deserializer as part of the ORT start up process.
     * Some values need to be processed before being used in the OSCake applications. Therefore, a singleton
     * of [OSCakeConfigParams] is created and filled with the original/adapted values. The original
     * instance of [OSCakeConfiguration] is kept for reporting reasons in the output (oscc-file: config).
     */
    companion object {
        private lateinit var osCakeConfig: OSCakeConfiguration
        private val commandLineParams: MutableMap<String, String> = mutableMapOf()

        /**
         * [setConfigParams] contains logical checks and combinations of configuration entries set in
         * osCakeConfig and sets the values in [OSCakeConfigParams]
         */
        internal fun setConfigParams(options: Map<String, String>) {
            osCakeConfig = getOSCakeConfiguration(options["configFile"]!!)
            require(isValidFolder(osCakeConfig.sourceCodesDir)) {
                "Invalid or missing config entry found for \"sourceCodesDir\" in oscake.conf"
            }
            // generate the commandline info and configuration info for the oscc file
            options.forEach {
                commandLineParams[it.key] = it.value
            }
            OSCakeConfigParams.setParams(OSCakeConfigInfo(commandLineParams, osCakeConfig))
        }
        /**
         * fetches the options which were passed via "-O OSCake=...=..."
         */
        private fun getOSCakeConfiguration(fileName: String): OSCakeConfiguration {
            val config = ConfigLoaderBuilder.default()
                .addPropertySource(PropertySource.file(File(fileName)))
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
