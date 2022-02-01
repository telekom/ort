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

import java.io.File
import java.io.FileNotFoundException

import kotlin.collections.HashMap

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.config.ScannerOptions
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.reporters.evaluatedmodel.DependencyTreeNode
import org.ossreviewtoolkit.reporter.reporters.evaluatedmodel.EvaluatedModel
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ConfigInfo
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.CopyrightTextEntry
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.FileInfoBlock
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.LicenseTextEntry
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ModeSelector
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeConfigParams
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeConfiguration
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeRoot
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Pack
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.REPORTER_LOGGER
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ScopeLevel
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.getLicensesFolderPrefix
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.handleOSCakeIssues
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.isInstancedLicense
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.zipAndCleanUp

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
        prepareNativeScanResults(input.ortResult.scanner?.config?.options, OSCakeConfiguration.params)
        val scanDict = getNativeScanResults(input, OSCakeConfiguration.params)
        removeMultipleEqualLicensesPerFile(scanDict)
        val osc = ingestAnalyzerOutput(input, scanDict, outputDir, OSCakeConfiguration.params)

        OSCakeConfiguration.params.forceIncludePackages.filter { !it.value }.forEach {
            logger.log("Package \"${it.key}\" is configured to be present due to \"forceIncludePackages-List\", " +
                    "but was not found!", Level.WARN, it.key, phase = ProcessingPhase.POST)
        }

        if (OSCakeLoggerManager.hasLogger(REPORTER_LOGGER)) {
            handleOSCakeIssues(osc.project, logger, OSCakeConfiguration.params.issuesLevel)
        }
        // transform result into json output
        val objectMapper = ObjectMapper()
        val outputFile = outputDir.resolve(reportFilename)
        outputFile.bufferedWriter().use {
            it.write(objectMapper.writeValueAsString(osc.project))
        }
        return listOf(outputFile)
    }

    /**
     * If the folder "native-scan-results" is empty, the raw data files from scanner are copied. The folder for
     * the raw data files is configured in ort.conf (scanner.options.ScanCode) as parameter "--json"
     */
    private fun prepareNativeScanResults(options: Map<String, ScannerOptions>?, params: OSCakeConfigParams) {
        // get folder name "rawDir" of scanner files from file "scan-result.yml"
        val scannerCommandLineParams = (options?.get("ScanCode")?.get("commandLine") ?: "").split(" ")
        val ind = scannerCommandLineParams.indexOfFirst { it == "--json" }
        require(ind > 0) { "The scanner was run without option --json, therefore no scan data exists!" }

        val scannerRaw = scannerCommandLineParams[ind + 1]
        val rawDir = File(scannerRaw).parent

        val rawDataExists = File(rawDir).exists() && File(rawDir).listFiles().isNotEmpty()
        val ortScanResultsDir = File(params.ortScanResultsDir)
        var scanDataExists = ortScanResultsDir.exists() && ortScanResultsDir.listFiles().isNotEmpty()

        require(!(!rawDataExists && !scanDataExists)) {
                "No native-scan-results found! Check folder $rawDir or re-run the scanner once again!" }

        if (rawDataExists) {
            // copy raw data to native-scan-results
            if (!ortScanResultsDir.exists()) ortScanResultsDir.mkdir()
            File(rawDir).listFiles().forEach {
                var fileName = getOriginalFolderName(it)
                require(fileName != null) { "Cannot identify file name in \"${it.name}\" in \"$rawDir\"! " +
                        "Clean up the folders \"$rawDir\" and ${ortScanResultsDir.name} and re-run the scanner first!" }

                fileName += "scan-results_ScanCode.json"
                if (ortScanResultsDir.resolve(fileName).exists()) logger.log("File $fileName (${it.name}) " +
                        "already exists and will be overwritten in ${params.ortScanResultsDir}!", Level.INFO,
                    phase = ProcessingPhase.PRE)

                it.copyTo(ortScanResultsDir.resolve(fileName), true)
            }
            // delete rawData
            File(rawDir).deleteRecursively()
            File(rawDir).mkdir()
            logger.log("ScanCode raw data files were copied from $rawDir to ${params.ortScanResultsDir}",
                Level.INFO, phase = ProcessingPhase.PRE)
        }
    }

    /**
     * Read the given file and extract the original folder name of the json file in "input" line
     * e.g. "..AppData\\Local\\Temp\\ort-ScanCode2604498589299189126\\Maven\\junit\\junit\\4.11"
     * result: Maven/junit/junit/4.11
     */
    private fun getOriginalFolderName(fileName: File): String? {
        var ret: String? = null
        if (fileName.isFile && fileName.length() > 0L) {
            val node = jsonMapper.readTree(fileName)

            node["headers"][0]["options"]["input"][0]?.let {
                val inps = if (it.toString().contains("\\")) it.toString().replace("\\\\", "\\")
                    .replace("\\", "/").replace("\"", "").split("/") else
                        it.toString().replace("\"", "").split("/")
                val ind = inps.indexOfFirst { it.startsWith("ort-ScanCode") }
                ret = ""
                for (n in ind + 1 until inps.size) {
                    ret += "${inps[n]}/"
                }
            }
        }
        return ret
    }

    /**
     * If a file contains more than one license text entry for a specific license and the difference of the
     * scores of the licenses is greater than 1, then the entry is removed - the one with the highest score remains.
     */
    private fun removeMultipleEqualLicensesPerFile(scanDict: MutableMap<Identifier,
            MutableMap<String, FileInfoBlock>>) {
        scanDict.forEach { (pkg, fibMap) ->
            fibMap.forEach { (_, fib) ->
                // create a map consisting of key=license, value=List of LicenseTextEntry
                val licMap: MutableMap<String, MutableList<LicenseTextEntry>> = mutableMapOf()
                fib.licenseTextEntries.forEach {
                    (licMap.getOrPut(it.license!!) { mutableListOf() }).add(it)
                }
                if (licMap.isNotEmpty()) {
                    val lteToRemove = mutableListOf<LicenseTextEntry>()
                    licMap.filterValues { it.size > 1 }.forEach { (_, lteList) ->
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
     *      3. download sources when needed (e.g. for instanced licenses)
     *      4. process the infos from the scanner
     */
    private fun ingestAnalyzerOutput(
        input: ReporterInput,
        scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
        outputDir: File,
        cfg: OSCakeConfigParams
    ): OSCakeRoot {
        val pkgMap = mutableMapOf<Identifier, org.ossreviewtoolkit.model.Package>()
        val osc = OSCakeRoot()
        osc.project.complianceArtifactCollection.cid = input.ortResult.getProjects().first().id.toCoordinates()
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
                    postActivities(tmpDirectory)
                }
            }
        }

        osc.project.complianceArtifactCollection.archivePath = "./" +
                input.ortResult.getProjects().first().id.name + ".zip"

        osc.project.config = ConfigInfo(OSCakeConfiguration.osCakeConfigInfo)

        if (OSCakeConfiguration.params.hideSections.isNotEmpty() == true) {
            if (osc.project.hideSections(OSCakeConfiguration.params.hideSections, tmpDirectory)) {
                osc.project.containsHiddenSections = true
            }
        }

        zipAndCleanUp(outputDir, tmpDirectory, osc.project.complianceArtifactCollection.archivePath,
            logger, ProcessingPhase.POST)

        cfg.onlyIncludePackages.filter { !it.value }.forEach { (identifier, _) ->
            logger.log("packageRestrictions are enabled, but the package [$identifier] was not found",
                Level.WARN, identifier, phase = ProcessingPhase.PROCESS)
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
            pp.first().also { scanResult ->
                try {
                    val fileInfoBlockDict = HashMap<String, FileInfoBlock>()
                    val nsr = getNativeScanResultJson(
                        key,
                        cfg.ortScanResultsDir
                    )

                    scanResult.summary.licenseFindings
                        .filter { !(it.license.toString() == "NOASSERTION" && cfg.ignoreNOASSERTION) }
                        .filter { !(it.license.toString().startsWith("LicenseRef") &&
                                cfg.ignoreLicenseRef) }
                        .filter { !(it.license.toString().startsWith("LicenseRef-scancode") &&
                                it.license.toString().contains("unknown") && cfg.ignoreLicenseRefScancodeUnknown) }
                        .forEach {
                        val fileInfoBlock =
                            fileInfoBlockDict.getOrPut(it.location.path) { FileInfoBlock(it.location.path) }

                        LicenseTextEntry().apply {
                            license = it.license.toString()
                            isInstancedLicense = isInstancedLicense(input, it.license.toString())
                            startLine = it.location.startLine
                            endLine = it.location.endLine
                            combineNativeScanResults(fileInfoBlock.path, this, nsr)
                            fileInfoBlock.licenseTextEntries.add(this)
                            license?.let { lic ->
                                if (lic.startsWith("LicenseRef")) logger.log("LicenseReference found:" +
                                        " \"$lic\" in File: \"${fileInfoBlock.path}\"", Level.WARN, key,
                                    phase = ProcessingPhase.PRE)
                            }
                        }
                    }
                    scanResult.summary.copyrightFindings.forEach {
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
                    logger.log("Native scan result was not found: ${fileNotFound.message}",
                        Level.ERROR, key, phase = ProcessingPhase.SCANRESULT)
                }
            }
        }
        return scanDict
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
                        " 'ortScanResultsDir' ")
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
        // include package despite the dependency-granularity level
        if (OSCakeConfiguration.params.forceIncludePackages.contains(id)) {
            OSCakeConfiguration.params.forceIncludePackages[id] = true
            return true
        }

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
