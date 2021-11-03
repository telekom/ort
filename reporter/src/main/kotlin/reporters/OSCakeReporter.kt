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

package org.ossreviewtoolkit.reporter.reporters

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.fp.getOrElse

import java.io.File
import java.io.FileNotFoundException

import kotlin.collections.HashMap

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.model.DependencyTreeNode
import org.ossreviewtoolkit.reporter.model.EvaluatedModel
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.CopyrightTextEntry
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.CurationManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.FileInfoBlock
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.LicenseTextEntry
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ModeSelector
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeConfigParams
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeConfiguration
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeRoot
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeWrapper
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Pack
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.REPORTER_LOGGER
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ScopeLevel
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.getLicensesFolderPrefix
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.handleOSCakeIssues
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.isInstancedLicense
import org.ossreviewtoolkit.utils.packZip

/**
 * A Reporter that creates the output for the Tdosca/OSCake projects
 */
class OSCakeReporter : Reporter {
    override val reporterName = "OSCake"
    private val reportFilename = "OSCake-Report.oscc"
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(REPORTER_LOGGER) }

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        // check configuration - cancel processing if necessary
        require(isValidFile(options, "configFile")) {
            "Value for 'OSCakeConf' is not a valid file!\n Add -O OSCake=configFile=... to the commandline"
        }
        OSCakeConfiguration.setConfigParams(options)
        // relay issues from ORT
        input.ortResult.analyzer?.result?.let {
            if (it.hasIssues)logger.log("Issue in ORT-ANALYZER - please check ORT-logfile or console output " +
                    "or scan_result.yml", Level.WARN, phase = ProcessingPhase.ORIGINAL)
        }
        input.ortResult.scanner?.results?.let {
            if (it.hasIssues) logger.log("Issue in ORT-SCANNER - please check ORT-logfile or console output " +
                    "or scan-result.yml", Level.WARN, phase = ProcessingPhase.ORIGINAL)
        }

        // start processing
        val scanDict = getNativeScanResults(input, OSCakeConfiguration.params)
        removeMultipleEqualLicensesPerFile(scanDict)
        val osc = ingestAnalyzerOutput(input, scanDict, outputDir, OSCakeConfiguration.params)
        if (OSCakeLoggerManager.hasLogger(REPORTER_LOGGER)) {
            handleOSCakeIssues(osc.project, logger, OSCakeConfiguration.params.issuesLevel)
        }

        // transform result into json output
        val objectMapper = ObjectMapper()
        val outputFile = outputDir.resolve(reportFilename)
        outputFile.bufferedWriter().use {
            it.write(objectMapper.writeValueAsString(osc.project))
        }
        // process curations
        if (OSCakeConfiguration.params.curationsEnabled) CurationManager(osc.project,
            outputDir, reportFilename).manage()

        return listOf(outputFile)
    }

    /**
     * If a file contains more than one license text entry for a specific license and the difference of the
     * scores of the licenses is greater than 1, then the entry is removed - the one with the highest score remains.
     */
    private fun removeMultipleEqualLicensesPerFile(scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>) {
        scanDict.forEach { (pkg, fibMap) ->
            fibMap.forEach { (_, fib) ->
                // create a map consisting of key=license, value=List of LicenseTextEntry
                val licMap: MutableMap<String, MutableList<LicenseTextEntry>> = mutableMapOf()
                fib.licenseTextEntries.forEach {
                    (licMap.getOrPut(it.license!!) { mutableListOf<LicenseTextEntry>() }).add(it)
                }
                if (licMap.isNotEmpty()) {
                    val lteToRemove = mutableListOf<LicenseTextEntry>()
                    licMap.filterValues { it.size > 1 } .forEach { (_, lteList) ->
                        val sortedList = lteList.toMutableList()
                        sortedList.sortByDescending { it.score }
                        val highest = sortedList[0]
                        for (i in 1 until lteList.size) {
                            // check if there is more than 1 percent difference in the scores
                            if (highest.score - sortedList[i].score > 1) {
                                lteToRemove.add(sortedList[i])
                                logger.log(
                                    "License text finding for license \"${highest.license}\" was removed, " +
                                            "due to multiple similar entries in the file!",
                                    Level.DEBUG, pkg, fib.path, highest, ScopeLevel.FILE, ProcessingPhase.PRE)
                            }
                        }
                    }
                    if (lteToRemove.isNotEmpty()) fib.licenseTextEntries.removeAll(lteToRemove)
                }
            }
        }

    }

    /**
     * Ingest analyzer output:
     *      1. create an entry for each project and included package which should be handled
     *      2. create a temporary folder to hold the identified files to archive
     *      3. download sources when needed (e.g. for instanced licenes)
     *      4. process the infos from the scanner
     */
    private fun ingestAnalyzerOutput(
        input: ReporterInput,
        scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
        outputDir: File,
        cfg: OSCakeConfigParams
    ): OSCakeRoot {
        val pkgMap = mutableMapOf<Identifier, org.ossreviewtoolkit.model.Package>()
        val osc = OSCakeRoot(input.ortResult.getProjects().first().id.toCoordinates())
        // evaluated model contains a dependency tree of packages with its corresponding levels (=depth in the tree)
        val evaluatedModel = EvaluatedModel.create(input)

        // prepare projects and packages
        input.ortResult.analyzer?.result?.projects?.
            filter { cfg.onlyIncludePackages.isEmpty() ||
                    (cfg.onlyIncludePackages.isNotEmpty() && cfg.onlyIncludePackages.contains(it.toPackage().id)) }?.
        forEach {
            val pkg = it.toPackage()
            pkgMap[pkg.id] = it.toPackage()
            Pack(it.id, it.vcsProcessed.url, input.ortResult.repository.vcs.path)
                .apply {
                    declaredLicenses.add(it.declaredLicensesProcessed.spdxExpression.toString())
                    osc.project.packs.add(this)
                    reuseCompliant = isREUSECompliant(this, scanDict)
                }
        }

        var cntLevelExcluded = 0
        input.ortResult.analyzer?.result?.packages?.
            filter { !input.ortResult.isExcluded(it.pkg.id) }?.
            filter { cfg.onlyIncludePackages.isEmpty() ||
                (cfg.onlyIncludePackages.isNotEmpty() && cfg.onlyIncludePackages.contains(it.pkg.id)) }?.
        forEach {
            if (cfg.dependencyGranularity < Int.MAX_VALUE && cfg.onlyIncludePackages.isEmpty()) {
                if (!treeLevelIncluded(evaluatedModel.dependencyTrees, cfg.dependencyGranularity, it.pkg.id)) {
                    cntLevelExcluded++
                    return@forEach
                }
            }
            val pkg = it.pkg
            pkgMap[pkg.id] = it.pkg
            Pack(it.pkg.id, it.pkg.vcsProcessed.url, "")
                .apply {
                    declaredLicenses.add(it.pkg.declaredLicensesProcessed.spdxExpression.toString())
                    osc.project.packs.add(this)
                    reuseCompliant = isREUSECompliant(this, scanDict)
                }
        }
        if (cntLevelExcluded > 0) logger.log("Attention: 'dependency-granularity' is restricted to level:" +
                " ${cfg.dependencyGranularity} via option! $cntLevelExcluded package(s) were excluded!", Level.INFO,
                phase = ProcessingPhase.PROCESS)

        val tmpDirectory = kotlin.io.path.createTempDirectory(prefix = "oscake_").toFile()

        osc.project.packs.filter { scanDict.containsKey(it.id) }.forEach { pack ->
            // makes sure that the "pack" is also in the scanResults-file and not only in the
            // "native-scan-results" (=scanDict)
            input.ortResult.scanner?.results?.scanResults?.containsKey(pack.id)?.let {
                ModeSelector.getMode(pack, scanDict, input, pkgMap).apply {
                    // special case when packages with the same id but different provenances exist, only the
                    // first is taken
                    input.ortResult.scanner?.results?.scanResults?.get(pkgMap[pack.id]!!.id)?.let {
                        if (it.size > 1) {
                            logger.log("Package has more than one provenance! " +
                                    "Only the first one is taken into/from the sourcecode folder!", Level.WARN, pack.id,
                                phase = ProcessingPhase.PROCESS)
                        }
                    }
                    val provenance = input.ortResult.scanner?.results?.scanResults!![pack.id]!!.first().provenance
                    downloadSourcesWhenNeeded(pack, scanDict, provenance)
                    fetchInfosFromScanDictionary(OSCakeConfiguration.params.sourceCodesDir, tmpDirectory, provenance)
                    postActivities()
                }
            }
        }

        osc.project.complianceArtifactCollection.archivePath = "./" +
                input.ortResult.getProjects().first().id.name + ".zip"

        zipAndCleanUp(outputDir, tmpDirectory, osc.project.complianceArtifactCollection.archivePath)

        cfg.onlyIncludePackages.filter { !it.value }.forEach { (identifier, _) ->
            logger.log("packageRestrictions are enabled, but the package [$identifier] was not found",
                Level.WARN, phase = ProcessingPhase.PROCESS)
        }
        return osc
    }

    /**
     * [isREUSECompliant] returns true as soon as a license entry is found in a folder
     * called "LICENSES/"
     */
    private fun isREUSECompliant(pack: Pack, scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>):
            Boolean = scanDict[pack.id]?.any { it.key.startsWith(getLicensesFolderPrefix(pack.packageRoot)) } ?: false

    /** Reads the scanner result and creates a Hashmap based on the package id (=key) e.g.: "Maven:junit:junit:4.11"
     * and the value (=[FileInfoBlock]). Each of the entries consists of a Hashmap based on the path of the existing
     * files and a list of [LicenseTextEntry]. The latter is updated with infos of the native scan result
     */
    private fun getNativeScanResults(
        input: ReporterInput,
        cfg: OSCakeConfigParams
    ): MutableMap<Identifier, MutableMap<String, FileInfoBlock>> {

        val scanDict = mutableMapOf<Identifier, MutableMap<String, FileInfoBlock>>()

        input.ortResult.scanner?.results?.scanResults?.
            filter { !input.ortResult.isExcluded(it.key) }?.
            filter { cfg.onlyIncludePackages.isEmpty() ||
                    (cfg.onlyIncludePackages.isNotEmpty() && cfg.onlyIncludePackages.contains(it.key)) }?.
            forEach { (key, pp) ->
            if (pp.size > 1) logger.log("Package has more than one provenance! " +
                            "Only the first one is taken!", Level.WARN, key, phase = ProcessingPhase.SCANRESULT)
            if (cfg.onlyIncludePackages.isNotEmpty()) cfg.onlyIncludePackages[key] = true
            pp.first().also {
                try {
                    val fileInfoBlockDict = HashMap<String, FileInfoBlock>()
                    val nsr = getNativeScanResultJson(
                        key,
                        getScanResultsDir(key, cfg.ortScanResultsDir, cfg.scanResultsCacheEnabled,
                            cfg.oscakeScanResultsDir)
                    )

                    it.summary.licenseFindings.forEach {
                        val fileInfoBlock =
                            fileInfoBlockDict.getOrPut(it.location.path) { FileInfoBlock(it.location.path) }

                        LicenseTextEntry().apply {
                            license = it.license.toString()
                            // NOTE:
//                            if (it.license.toString().startsWith("LicenseRef-")) {
//                                logger.log(
//                                    "Changed <${it.license}> to <NOASSERTION> in package: " +
//                                            "${key.name} - ${it.location.path}", Level.INFO, key, fileInfoBlock.path
//                                )
//                                license = "NOASSERTION"
//                            }
                            isInstancedLicense = isInstancedLicense(input, it.license.toString())
                            startLine = it.location.startLine
                            endLine = it.location.endLine
                            combineNativeScanResults(fileInfoBlock.path, this, nsr)
                            fileInfoBlock.licenseTextEntries.add(this)
                        }
                    }
                    it.summary.copyrightFindings.forEach {
                        fileInfoBlockDict.getOrPut(it.location.path) {
                            FileInfoBlock(it.location.path)
                        }.apply {
                            copyrightTextEntries.add(
                                CopyrightTextEntry(
                                    it.location.startLine,
                                    it.location.endLine, it.statement
                                )
                            )
                        }
                    }
                    scanDict[key] = fileInfoBlockDict
                } catch (fileNotFound: FileNotFoundException) {
                    // if native scan results are not found for one package, we continue, but log an error
                    logger.log("Native scan result was not found: ${fileNotFound.message.toString()}",
                        Level.ERROR, null, phase = ProcessingPhase.SCANRESULT)
                }
            }
        }
        if (cfg.deleteOrtNativeScanResults && cfg.scanResultsCacheEnabled &&
            cfg.ortScanResultsDir != null) File(cfg.ortScanResultsDir).deleteRecursively()

        return scanDict
    }

    /**
     * if "ort-native-scan-results"[ortScanResultsDir] exists and [scanResultsCacheEnabled] is enabled, the package
     * is copied to the "oscake-native-scan-results-cache" [oscakeScanResultsDir] and deleted from origin.
     * Method returns the directory where to search for the scan results
     */
    private fun getScanResultsDir(
        id: Identifier,
        ortScanResultsDir: String?,
        scanResultsCacheEnabled: Boolean,
        oscakeScanResultsDir: String?
    ): String? {
        var path = ortScanResultsDir
        if (!scanResultsCacheEnabled || oscakeScanResultsDir == null) return path

        val subfolder = id.toPath()

        if (ortScanResultsDir != null && File(ortScanResultsDir).resolve(subfolder).exists()) {
            File("$oscakeScanResultsDir/$subfolder").also {
                if (it.exists()) it.deleteRecursively()
                File("$ortScanResultsDir/$subfolder").copyRecursively(it)
                path = "$oscakeScanResultsDir"
            }
        } else {
            path = "$oscakeScanResultsDir"
            if (ortScanResultsDir != null && !File("$oscakeScanResultsDir/$subfolder").exists()) {
                logger.log("Scan results for $subfolder does not exist - neither in $ortScanResultsDir nor in " +
                            "$oscakeScanResultsDir", Level.ERROR, phase = ProcessingPhase.SCANRESULT
                )
            }
        }
        return path
    }

    /**
     * returns the json for the requested package [id]
     */
    private fun getNativeScanResultJson(
        id: Identifier,
        nativeScanResultsDir: String?
    ): JsonNode {
        val subfolder = id.toPath()
        val filePath = "$nativeScanResultsDir/$subfolder/scan-results_ScanCode.json"

        val scanFile = File(filePath)
        if (!scanFile.exists()) {
            throw FileNotFoundException(
                "Cannot find native scan result \"${scanFile.absolutePath}\". Check configuration settings for " +
                        " 'scanResultsCache' and/or 'ortScanResultsDir' ")
        }
        var node: JsonNode = EMPTY_JSON_NODE
        if (scanFile.isFile && scanFile.length() > 0L) {
            node = jsonMapper.readTree(scanFile)
        }

        return node
    }

    /**
     * Searches for specific infos (e.g. flag: is_license_text) in native scan results (represented in json)
     * based on [path], "start_line", "end_line" and updates this information in the licenseTextEntry
     */
    private fun combineNativeScanResults(
        path: String,
        licenseTextEntry: LicenseTextEntry,
        node: JsonNode,
    ) {
        for (file in node["files"]) {
            if (file["path"].asText() == path) {
                for (license in file["licenses"]) {
                    if (licenseTextEntry.startLine == license["start_line"].intValue() &&
                        licenseTextEntry.endLine == license["end_line"].intValue()) {
                        licenseTextEntry.isLicenseText = license["matched_rule"]["is_license_text"].asBoolean()
                        licenseTextEntry.isLicenseNotice = license["matched_rule"]["is_license_notice"].asBoolean()
                                || license["matched_rule"]["is_license_reference"].asBoolean()
                                || license["matched_rule"]["is_license_tag"].asBoolean()
                        licenseTextEntry.score = license["score"].asDouble()
                    }
                }
            }
        }
    }

    private fun zipAndCleanUp(outputDir: File, tmpDirectory: File, zipFileName: String) {
        val targetFile = File(outputDir.path + "/" + zipFileName)
        if (targetFile.exists()) targetFile.delete()
        tmpDirectory.packZip(targetFile)
        tmpDirectory.deleteRecursively()
    }

    /**
     * Recursive method [treeLevelIncluded] searches for a specific package represented by [Identifier] in the
     * dependency trees. If the level is smaller or equal to [optionLevel] than the return value is set to true
     */
    private fun treeLevelIncluded(
        dependencyTrees: List<DependencyTreeNode>,
        optionLevel: Int,
        id: Identifier
    ): Boolean {
        var rc = false
        dependencyTrees.forEach { dependencyTreeNode ->
            // take only nodes for packages and not nodes for structuring (e.g. defining scopes)
            if (dependencyTreeNode.pkg != null && dependencyTreeNode.pkg.id == id) {
                if (dependencyTreeNode.pkg.levels.any { it <= optionLevel }) rc = true
            }
            if (!rc) rc = treeLevelIncluded(dependencyTreeNode.children, optionLevel, id)
        }
        return rc
    }

    /**
     * checks if the value of the optionName in map is a valid file
     */
    private fun isValidFile(map: Map<String, String>, optionName: String): Boolean =
        if (map[optionName] != null) File(map[optionName]!!).exists() &&
                File(map[optionName]!!).isFile else false
}
