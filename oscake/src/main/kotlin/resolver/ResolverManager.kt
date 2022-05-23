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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.model.readValueOrNull
import org.ossreviewtoolkit.oscake.RESOLVER_LOGGER
import org.ossreviewtoolkit.oscake.common.ActionInfo
import org.ossreviewtoolkit.oscake.common.ActionManager
import org.ossreviewtoolkit.oscake.curator.CurationManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Pack
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Project
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.config.OSCakeConfigParams
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.FOUND_IN_FILE_SCOPE_DECLARED
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.ProcessingPhase
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.getNativeScanResultJson
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.handleOSCakeIssues

/**
 * The [ResolverManager] handles the entire resolver process: reads and analyzes the resolver files,
 * prepares the list of packages (passed by the reporter), applies the resolver actions and writes the results into
 * the output files - one oscc compliant file in json format and a zip-archive containing the license texts.
 */
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

) : ActionManager(
    project,
    outputDir,
    reportFilename,
    config,
    ActionInfo.resolver(config.resolver?.issueLevel ?: -1), commandLineParams
) {
    /**
     * The [resolverProvider] contains a list of [ResolverPackage]s to be applied.
     */
    private var resolverProvider = ResolverProvider(File(config.resolver?.directory!!))

    /**
     * Get license information from analyzer_result.yml, in order to get the "declared_licenses_processed" info
     */
    private val analyzedPackageLicenses = fetchPackageLicensesFromAnalyzer()

    /**
     * Get license information from native-scan-results, in order to get compound licenses
     */
    private val scannedFileLicenses = fetchPackageLicensesFromScanner()

    /**
     * The method takes the reporter's output, checks and updates the reported "hasIssues", applies the
     * resolver actions, reports emerged issues and finally, writes the output files.
     */
    internal fun manage() {
        // 1. Automatically create resolving actions for packages with more than one license (either in
        // analyzer-results.yml or in a file license) which have no manually created resolving action defined
        project.packs.forEach { pack -> resolverProvider.getActionFor(pack.id) ?: appendAction(pack) }

        // 2. Automatically create resolving actions found in native-scan-results with compound licenses
        project.packs.forEach { pack -> resolverProvider.getActionFor(pack.id) ?: appendActionFromScan(pack) }

        // 3. Process resolver-package if it's valid, applicable and pack is not reuse-compliant
        OSCakeConfigParams.setParamsFromProject(project)
        project.packs.forEach {
            resolverProvider.getActionFor(it.id)?.process(it, archiveDir, logger)
        }

        // 4. generate a resolver template for unresolved multiple licenses
        if (commandLineParams.containsKey("generateTemplate")) generateResolverTemplate()

        // 5. report [OSCakeIssue]s
        if (OSCakeLoggerManager.hasLogger(RESOLVER_LOGGER)) handleOSCakeIssues(
            project,
            logger,
            config.resolver?.issueLevel ?: -1
        )
        // 6. take care of issue level settings to create the correct output format
        takeCareOfIssueLevel()
        // 7. generate .zip and .oscc files
        createResultingFiles(archiveDir)
    }

    /**
     * If a fileLicensing in a package contains more than one license entry then it could be a compound license which
     * was not treated by the resolver packages yet. Therefore, a template - structured as a resolving action -
     * is generated as a yml file for all missing/open items. This file called "template.yml.tmp" can be found in the
     * directory where the resolver actions are stored. This template can be used to manually add resolver blocks -
     * by copy/paste.
     */
    private fun generateResolverTemplate() {
        val resList = mutableListOf<ResolverPackage>()
        project.packs.forEach { pack ->
            pack.fileLicensings
                .filter { fileLicensing -> fileLicensing.licenses.map { it.license }.toSet().size > 1 }
                .forEach { fileLicensing ->
                    val licenses = fileLicensing.licenses.map { it.license }.toSet()
                    val resolverPackage = resList.firstOrNull { it.id == pack.id } ?:
                        (ResolverPackage(pack.id).also { resList.add(it) })
                    val resolverBlock = resolverPackage.resolverBlocks.firstOrNull { it.licenses.toSet() == licenses }
                    if (resolverBlock == null)
                        resolverPackage.resolverBlocks.add(
                            ResolverBlock(
                                licenses.toList(),
                                licenses.toList().joinToString(" OR "),
                                arrayListOf(fileLicensing.scope)
                            )
                        )
                    else {
                        if (!resolverBlock.scopes.contains(fileLicensing.scope)) {
                            resolverBlock.scopes.add(fileLicensing.scope)
                        }
                    }
                }
        }
        writeTemplate(resList)
    }

    private fun writeTemplate(resList: MutableList<ResolverPackage>) {
        val outputFile = File(config.resolver?.directory!!).resolve("template.yml.tmp")
        val objectMapper = ObjectMapper(YAMLFactory())
        try {
            outputFile.bufferedWriter().use {
                it.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resList))
            }
        } catch (e: IOException) {
            logger.log(
                "Error when writing json file: \"$outputFile\".\n ${e.message} ",
                Level.ERROR,
                phase = ProcessingPhase.RESOLVING
            )
        }
    }

    /**
     * Generate default actions based on analyzer-results.yml info - scope is set to empty string --> valid for
     * all files
     */
    private fun appendAction(pack: Pack) {
        // handle [DECLARED] licenses
        if (pack.defaultLicensings.any { it.path == FOUND_IN_FILE_SCOPE_DECLARED })
            analyzedPackageLicenses[pack.id]?.let { analyzerLicenses ->
                if (analyzerLicenses.mappedLicenses.toList().any { it?.contains(" AND ") == true })
                    logger.log(
                        "DECLARED License expression contains the operator \"AND\" and cannot be processed!",
                        Level.WARN,
                        phase = ProcessingPhase.RESOLVING
                    )
                else
                    resolverProvider.actions.add(
                        ResolverPackage(
                            pack.id,
                            mutableListOf(
                                ResolverBlock(
                                  analyzerLicenses.mappedLicenses.toList(), analyzerLicenses.declaredLicensesProcessed,
                                  arrayListOf("")
                                )
                            )
                        )
                    )
            }
        // handle all others if default licenses and declaredLicenses are equivalent
        else {
            if (pack.defaultLicensings.size < 2) return // not dual licensed
            analyzedPackageLicenses[pack.id]?.let { analyzerLicenses ->
                if (isEqual(
                        analyzerLicenses.mappedLicenses.toSet(),
                        pack.defaultLicensings.mapNotNull { it.license }.toSet()
                    )
                ) {
                    if (analyzerLicenses.declaredLicensesProcessed.split(" ").contains("AND"))
                        logger.log(
                            "License expression contains the operator \"AND\" and cannot be processed!",
                            Level.WARN,
                            phase = ProcessingPhase.RESOLVING
                        )
                    else
                        resolverProvider.actions.add(
                            ResolverPackage(
                                pack.id,
                                mutableListOf(
                                    ResolverBlock(
                                        analyzerLicenses.mappedLicenses.toList(),
                                        analyzerLicenses.declaredLicensesProcessed, arrayListOf("")
                                    )
                                )
                            )
                    )
                }
            }
        }
    }

    /**
     * Create resolver actions based on the native-scan-results
     */
    private fun appendActionFromScan(pack: Pack) =
        scannedFileLicenses.filter { it.key == pack.id }.forEach { (_, v) ->
            resolverProvider.actions.add(
                ResolverPackage(
                    pack.id,
                    mutableListOf(
                        ResolverBlock(
                            v.second.split(" OR ", " AND "),
                            v.second,
                            arrayListOf(v.first.replace("\"", ""))
                        )
                    )
                )
            )
        }

    private inline fun <reified T> isEqual(firstList: Set<T>, secondList: Set<T>): Boolean {
        if (firstList.size != secondList.size) return false
        return firstList.sortedBy { it.toString() }.toTypedArray() contentEquals secondList.sortedBy { it.toString() }
            .toTypedArray()
    }

    /**
     * Reads the native scan results file and searches for the tag "license_expressions". If the license
     * list contains only one item and this represents a compound license (with "OR" or "AND"), the license id is
     * replaced by the SPDX expression. The method returns a map with the package-id as key and a pair of
     * file-paths and its assigned license expression.
     */
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private fun fetchPackageLicensesFromScanner(): Map<Identifier, Pair<String, String>> {
        val licMap: MutableMap<Identifier, Pair<String, String>> = mutableMapOf()

        if (!commandLineParams.containsKey("nativeScanResultsDir") ||
            !File(commandLineParams["nativeScanResultsDir"]!!).exists()
        ) {
            logger.log(
                "Native scan results from ORT-Scanner not found or not provided!",
                Level.WARN,
                phase = ProcessingPhase.RESOLVING
            )
            return licMap
        }

        val nativeScanResultsDir = commandLineParams["nativeScanResultsDir"]
        project.packs.filter { it.fileLicensings.isNotEmpty() }.forEach {
            var lastFile: String? = null
            try {
                val nsr = getNativeScanResultJson(it.id, nativeScanResultsDir)
                for (file in nsr["files"]) {
                    lastFile = file["path"].asText()
                    val licList = mutableListOf<String>()
                    for (licExpr in file["license_expressions"]) licList.add(licExpr.asText())
                    if (checkLicenseExpression(licList)) {
                        val mapping = mutableListOf<Pair<String, String>>()
                        for (license in file["licenses"]) {
                            if (license["matched_rule"]["license_expression"].asText() == licList.first())
                                mapping.add(Pair(license["key"].asText(), license["spdx_license_key"].asText()))
                        }
                        licMap[it.id] = Pair(file["path"].asText(), mapKeyToSpdx(licList.first(), mapping))
                    }
                }
            } catch (fileNotFound: FileNotFoundException) {
                // if native scan results are not found for one package, let's continue, but log an error
                logger.log(
                    "Native scan result was not found (maybe the scanner found no license!): ${fileNotFound.message}",
                    Level.INFO,
                    it.id,
                    phase = ProcessingPhase.RESOLVING
                )
            } catch (nullPointer: NullPointerException) {
                // if specific json-Node is not found - may happen when the ScanCode-Scanner changes the output format
                logger.log(
                    "JSON-Node was not found in native-scan-results file-entry \"$lastFile\"",
                    Level.WARN,
                    it.id,
                    phase = ProcessingPhase.RESOLVING
                )
            }
        }
        return licMap
    }

    /**
     * Replaces the license id with the SPDX-ID
     */
    private fun mapKeyToSpdx(licenseExpression: String, mapping: List<Pair<String, String>>): String {
        var matchString = licenseExpression
        licenseExpression.split(" OR ", " AND ", " WITH ").forEach { license ->
            if (mapping.any { it.first == license })
                mapping.first { it.first == license }.let {
                    matchString = matchString.replace(license, it.second)
                }
        }
        return matchString
    }

    private fun checkLicenseExpression(licExpr: List<String>) =
        if (licExpr.size != 1) false
    else
        licExpr.any { isValidLicense(it) }

    /**
     * Fetch infos about licenses directly from analyzer_results.yml by using the ORT function [readValueOrNull]
     */
    private fun fetchPackageLicensesFromAnalyzer(): Map<Identifier, AnalyzerLicenses> {
        val licMap: MutableMap<Identifier, AnalyzerLicenses> = emptyMap<Identifier, AnalyzerLicenses>().toMutableMap()

        if (commandLineParams.containsKey("analyzerFile") && File(commandLineParams["analyzerFile"]!!).exists()) {
            val ortResult = File(commandLineParams["analyzerFile"]!!).readValueOrNull<OrtResult>()

            ortResult?.analyzer?.result?.projects?.filter { it.declaredLicenses.size > 1 }?.forEach { project ->
                if (isValidLicense(project.declaredLicensesProcessed.spdxExpression.toString()))
                    licMap[project.id] = AnalyzerLicenses(
                        project.declaredLicenses,
                        project.declaredLicensesProcessed.spdxExpression.toString(),
                        project.declaredLicensesProcessed.mapped
                    )
            }
            ortResult?.analyzer?.result?.packages?.filter { !ortResult.isExcluded(it.pkg.id) }?.forEach { it ->
                if (it.pkg.declaredLicenses.size > 1) {
                    if (isValidLicense(it.pkg.declaredLicensesProcessed.spdxExpression.toString()))
                        licMap[it.pkg.id] = AnalyzerLicenses(
                            it.pkg.declaredLicenses,
                            it.pkg.declaredLicensesProcessed.spdxExpression.toString(),
                            it.pkg.declaredLicensesProcessed.mapped
                        )
                    else
                        logger.log(
                            "The package contains a compound license with \"AND\" - this is not supported " +
                                "by OSCake, yet!",
                            Level.WARN,
                            it.pkg.id,
                            phase = ProcessingPhase.RESOLVING
                        )
                }
            }
        } else
            logger.log(
                "Results from ORT-Analyzer not found or not provided!",
                Level.WARN,
                phase = ProcessingPhase.RESOLVING
            )
        return licMap
    }

    /**
     * Currently, only compound licenses with "OR" are allowed
     */
    private fun isValidLicense(license: String): Boolean = license.contains(" OR ") &&
            !license.contains(" AND ")
}
