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

package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.config

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.DistributionType
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Project
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.ProcessingPhase
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.REPORTER_LOGGER

/**
 * [OSCakeConfigParams] class - holds processed data from [OSCakeConfiguration]. The description of the properties
 * can be found in [OSCakeConfiguration] directly
 */
object OSCakeConfigParams {
    var ortScanResultsDir: String? = null
    var onlyIncludePackages: MutableMap<Identifier, Boolean> = mutableMapOf()
    var dependencyGranularity: Int = -1
    var issuesLevel: Int = -1
    var sourceCodesDir: String? = null
    var includeJsonPathInLogfile4ErrorsAndWarnings: Boolean = false
    var scopePatterns: List<String> = mutableListOf()
    var copyrightScopePatterns: List<String> = mutableListOf()
    var scopeIgnorePatterns: List<String> = mutableListOf()
    var forceIncludePackages: MutableMap<Identifier, Boolean> = mutableMapOf()
    var ignoreNOASSERTION: Boolean = false
    var ignoreLicenseRefScancodeUnknown: Boolean = false
    var hideSections: List<String> = emptyList()
    var lowerCaseComparisonOfScopePatterns: Boolean = true
    var ignoreLicenseRef: Boolean = false
    var licenseScoreThreshold: Int = 0
    var distributionMap: MutableMap<String, String> = mutableMapOf()
    var prettyPrint = false
    var ignoreFromChecks: Boolean = false
    lateinit var osCakeConfigInformation: OSCakeConfigInfo

    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(REPORTER_LOGGER) }
    /**
     * Some functions are calling functions from OSCakeReporter, and these are in need of some configuration info.
     * Therefore, parts of the configuration are reconstructed directly from the project (oscc-file)
     */
    fun setParamsFromProject(project: Project) {
        project.config?.metadatamanager?.commandLineParams?.get("ignoreFromChecks")?.let {
            ignoreFromChecks = it.toBoolean()
        }
        project.config?.reporter?.let {
            setParams(
                OSCakeConfigInfo(
                    project.config?.reporter?.commandLineParams ?: emptyMap(),
                    project.config?.reporter?.configFile!!
                )
            )
        }
    }

    fun setParams(osCakeConfigInfo: OSCakeConfigInfo) {
        osCakeConfigInformation = osCakeConfigInfo

        dependencyGranularity = if (osCakeConfigInfo.commandLineParams["--dependency-granularity"]?.
            toIntOrNull() != null
        ) osCakeConfigInfo.commandLineParams["--dependency-granularity"]!!.toInt() else Int.MAX_VALUE

        osCakeConfigInfo.configFile.packageRestrictions?.let { packageRestriction ->
            if (packageRestriction.enabled != null && packageRestriction.enabled) {
                var infoStr = "The output is restricted - set in configuration - to the following package(s):\n"
                packageRestriction.onlyIncludePackages?.forEach {
                    onlyIncludePackages[Identifier(it)] = false
                    infoStr += "   ${Identifier(it)}\n"
                }
                logger.log(infoStr, Level.INFO, phase = ProcessingPhase.CONFIG)
                dependencyGranularity = Int.MAX_VALUE
                logger.log(
                    "commandline parameter \"dependency-granularity\" is overruled due to packageRestrictions",
                    Level.INFO,
                    phase = ProcessingPhase.CONFIG
                )
            }
        }
        osCakeConfigInfo.configFile.packageInclusions?.let { packageInclusion ->
            if (dependencyGranularity != Int.MAX_VALUE && packageInclusion.enabled != null &&
                packageInclusion.enabled
            ) {
                var infoStr = "The output is extended (set in configuration and despite of the commandline " +
                        "parameter: \"dependency-granularity\" ) with the following package(s):\n"
                packageInclusion.forceIncludePackages?.forEach {
                    forceIncludePackages[Identifier(it)] = false
                    infoStr += "   ${Identifier(it)}\n"
                }
                logger.log(infoStr, Level.INFO, phase = ProcessingPhase.CONFIG)
            }
        }

        osCakeConfigInfo.configFile.includeIssues?.enabled.let {
            issuesLevel = osCakeConfigInfo.configFile.includeIssues?.level ?: -1
            if (issuesLevel > 2) issuesLevel = 2
            if (it == false) issuesLevel = -1
        }

        osCakeConfigInfo.configFile.distributionMap?.forEach { item ->
            if (enumValueOfOrNull<DistributionType>(item.value) != null)
                distributionMap[item.key] = item.value
            else
                logger.log(
                    "Invalid config value \"${item.value}\" in distributionMap - must be one " +
                        "of ${DistributionType.values().map { it.name }} --> ignored",
                    Level.INFO,
                    phase = ProcessingPhase.CONFIG
                )
        }

        ortScanResultsDir = osCakeConfigInfo.configFile.ortScanResultsDir
        sourceCodesDir = osCakeConfigInfo.configFile.sourceCodesDir
        lowerCaseComparisonOfScopePatterns = osCakeConfigInfo.configFile.lowerCaseComparisonOfScopePatterns ?: true
        scopePatterns = osCakeConfigInfo.configFile.scopePatterns
        copyrightScopePatterns = (
                osCakeConfigInfo.configFile.copyrightScopePatterns + osCakeConfigInfo.configFile.scopePatterns
            ).toList()
        scopeIgnorePatterns = osCakeConfigInfo.configFile.scopeIgnorePatterns
        if (lowerCaseComparisonOfScopePatterns) {
            scopePatterns = scopePatterns.map { it.lowercase() }.distinct().toList()
            copyrightScopePatterns = copyrightScopePatterns.map { it.lowercase() }.distinct().toList()
            scopeIgnorePatterns = scopeIgnorePatterns.map { it.lowercase() }.distinct().toList()
        }
        ignoreNOASSERTION = osCakeConfigInfo.configFile.ignoreNOASSERTION ?: false
        ignoreLicenseRefScancodeUnknown = osCakeConfigInfo.configFile.ignoreLicenseRefScancodeUnknown ?: false
        ignoreLicenseRef = osCakeConfigInfo.configFile.ignoreLicenseRef ?: false
        hideSections = osCakeConfigInfo.configFile.hideSections ?: emptyList()
        licenseScoreThreshold = osCakeConfigInfo.configFile.licenseScoreThreshold ?: 0
        includeJsonPathInLogfile4ErrorsAndWarnings =
            osCakeConfigInfo.configFile.includeJsonPathInLogfile4ErrorsAndWarnings ?: false
        prettyPrint = osCakeConfigInfo.configFile.prettyPrint
    }
    /**
     * Returns an enum entry with the specified name or `null` if no such entry was found.
     */
    private inline fun <reified T : Enum<T>> enumValueOfOrNull(name: String): T? {
        return enumValues<T>().find { it.name == name }
    }
}
