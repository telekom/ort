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

package org.ossreviewtoolkit.oscake.resolver

import java.io.File
import java.util.SortedSet

import kotlin.io.path.createTempDirectory

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.model.readValueOrNull
import org.ossreviewtoolkit.oscake.RESOLVER_LOGGER
import org.ossreviewtoolkit.oscake.common.ActionInfo
import org.ossreviewtoolkit.oscake.common.ActionManager
import org.ossreviewtoolkit.oscake.curator.CurationManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.FOUND_IN_FILE_SCOPE_DECLARED
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeConfigParams
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Pack
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Project
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.handleOSCakeIssues
import org.ossreviewtoolkit.utils.common.unpackZip

internal data class AnalyzerLicenses(
    val declaredLicenses: SortedSet<String>,
    val declaredLicensesProcessed: String)

internal class ResolverManager(
    /**
    * The [project] contains the processed output from the OSCakeReporter - specifically a list of packages.
    */
    override val project: Project,
    /**
    * The generated files are stored in the folder [outputDir]
    */
    override val outputDir: File,
    /**
    * The name of the reporter's output file which is extended by the [CurationManager]
    */
    override val reportFilename: String,
    /**
    * Configuration in ort.conf
    */
    override val config: OSCakeConfiguration,
    /**
     * Map of passed commandline parameters
     */
    override val commandLineParams: Map<String, String>

) : ActionManager(project, outputDir, reportFilename, config,
    ActionInfo.resolver(config.resolver?.issueLevel ?: -1), commandLineParams) {

    /**
     * The [resolverProvider] contains a list of [ResolverPackage]s to be applied.
     */
    private var resolverProvider = ResolverProvider(File(config.resolver?.directory!!))

    /**
     * If resolve actions have to be applied, the reporter's zip-archive is unpacked into this temporary folder.
     */
    private val archiveDir: File by lazy {
        createTempDirectory(prefix = "oscakeAct_").toFile().apply {
            File(outputDir, project.complianceArtifactCollection.archivePath).unpackZip(this)
        }
    }

    /**
     * Get license information from analyzer_result.yml, in order to get the "declared_licenses_processed" info
     */
    private val analyzedPackageLicenses = fetchPackageLicensesFromAnalyzer()

    /**
     * The method takes the reporter's output, checks and updates the reported "hasIssues", applies the
     * resolver actions, reports emerged issues and finally, writes the output files.
     */
    internal fun manage() {
        // 1. create resolving actions for packages which have no resolving action defined
        project.packs/*.filter { pack -> pack.defaultLicensings.any { it.license == FOUND_IN_FILE_SCOPE_DECLARED } }*/
            .forEach { pack -> resolverProvider.getActionFor(pack.id) ?: appendAction(pack)
        }
        // 2. process resolver-package if it's valid and applicable
        project.packs.forEach {
            resolverProvider.getActionFor(it.id)?.apply {
                (this as ResolverPackage).dedupInResolveMode = config.resolver?.deduplicate ?: false
                process(it, OSCakeConfigParams.setParamsForCompatibilityReasons(project), archiveDir, logger)
            }
        }
        // 3. report [OSCakeIssue]s
        if (OSCakeLoggerManager.hasLogger(RESOLVER_LOGGER)) handleOSCakeIssues(project, logger,
            config.resolver?.issueLevel ?: -1)
        // 4. take care of issue level settings to create the correct output format
        takeCareOfIssueLevel()
        // 5. generate .zip and .oscc files
        createResultingFiles(archiveDir)
    }

    /**
     * Generate default actions based on analyzer-results.yml info - scope is set to empty string --> valid for
     * all files
     */
    private fun appendAction(pack: Pack) {
        // handle [DECLARED] licenses
        if (pack.defaultLicensings.any { it.license == FOUND_IN_FILE_SCOPE_DECLARED })
            analyzedPackageLicenses[pack.id]?.let {
                resolverProvider.actions.add(ResolverPackage(pack.id, it.declaredLicenses.toList(),
                    it.declaredLicensesProcessed, mutableListOf("")))
            }
        // handle all others if default licenses and declaredLicenses are equivalent
        else {
            analyzedPackageLicenses[pack.id]?.let { analyzerLicenses ->
                if (isEqual(analyzerLicenses.declaredLicenses.toSet(),
                        pack.defaultLicensings.mapNotNull { it.license }.toSet())) {
                    resolverProvider.actions.add(
                        ResolverPackage(
                            pack.id, analyzerLicenses.declaredLicenses.toList(),
                            analyzerLicenses.declaredLicensesProcessed, mutableListOf("")
                        )
                    )
                }
            }
        }
    }

    private inline fun <reified T> isEqual(firstList: Set<T>, secondList: Set<T>): Boolean {
        if (firstList.size != secondList.size) return false
        return firstList.sortedBy { it.toString() }.toTypedArray() contentEquals secondList.sortedBy { it.toString() }
            .toTypedArray()
    }

    /**
     * Fetch infos about licenses directly from analyzer_results.yml by using the ORT function [readValueOrNull]
     */
    private fun fetchPackageLicensesFromAnalyzer(): Map<Identifier, AnalyzerLicenses> {
        val licMap: MutableMap<Identifier, AnalyzerLicenses> = emptyMap<Identifier, AnalyzerLicenses>().toMutableMap()

        if (commandLineParams.containsKey("analyzerFile") && File(commandLineParams["analyzerFile"]!!).exists()) {
            val ortResult = File(commandLineParams["analyzerFile"]!!).readValueOrNull<OrtResult>()

            ortResult?.analyzer?.result?.projects?.forEach { project ->
                if (isValidLicense(project.declaredLicensesProcessed.spdxExpression.toString()))
                    licMap[project.id] = AnalyzerLicenses(project.declaredLicenses,
                        project.declaredLicensesProcessed.spdxExpression.toString())
            }
            ortResult?.analyzer?.result?.packages?.filter { !ortResult.isExcluded(it.pkg.id) }?.forEach { it ->
                if (isValidLicense(it.pkg.declaredLicensesProcessed.spdxExpression.toString()))
                    licMap[it.pkg.id] = AnalyzerLicenses(it.pkg.declaredLicenses,
                        it.pkg.declaredLicensesProcessed.spdxExpression.toString())
            }
        } else
            logger.log("Results from ORT-Analyzer not found or not provided!", Level.WARN,
                phase = ProcessingPhase.RESOLVING)
        return licMap
    }

    /**
     * Currently, only compound licenses with "OR" are allowed
     */
    private fun isValidLicense(declaredLicense: String): Boolean = declaredLicense.contains(" OR ")
            && !declaredLicense.contains(" AND ") && !declaredLicense.contains(" WITH ")
}
