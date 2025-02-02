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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.Options
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.reporters.evaluatedmodel.DependencyTreeNode
import org.ossreviewtoolkit.reporter.reporters.evaluatedmodel.EvaluatedModel
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ConfigInfo
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.CopyrightTextEntry
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.FileInfoBlock
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.LicenseTextEntry
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Pack
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Project
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.config.OSCakeConfigParams
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.config.OSCakeConfiguration
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.modes.ModeSelector
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.ProcessingPhase
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.REPORTER_LOGGER
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.ScopeLevel
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.getLicensesFolderPrefix
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.getNativeScanResultJson
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.handleOSCakeIssues
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.isInstancedLicense
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.zipAndCleanUp

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
        require(isValidFile(options)) {
            "Value for 'OSCakeConf' is not a valid file!\n Add -O OSCake=configFile=... to the commandline"
        }
        OSCakeConfiguration.setConfigParams(options)

        // relay issues from ORT
        if (input.ortResult.analyzer?.result?.hasIssues == true)
            logger.log(
                "Issue in ORT-ANALYZER - please check ORT-logfile or console output or analyzer-result.yml",
                Level.WARN,
                phase = ProcessingPhase.ORIGINAL
            )
        if (input.ortResult.scanner?.results?.hasIssues == true)
            logger.log(
                "Issue in ORT-SCANNER - please check ORT-logfile or console output or scan-result.yml",
                Level.WARN,
                phase = ProcessingPhase.ORIGINAL
            )

        // start processing
        prepareNativeScanResults(input.ortResult.scanner?.config?.options)
        val scanDict = getNativeScanResults(input)
        removeScoreBasedMultipleLicensesPerFile(scanDict)
        reportScoreBelowThreshold(scanDict)
        val project = ingestAnalyzerOutput(input, scanDict, outputDir)

        OSCakeConfigParams.forceIncludePackages.filter { !it.value }.forEach {
            logger.log(
                "Package \"${it.key}\" is configured to be present due to \"forceIncludePackages-List\", " +
                    "but was not found!",
                Level.WARN,
                it.key,
                phase = ProcessingPhase.POST
            )
        }

        if (OSCakeLoggerManager.hasLogger(REPORTER_LOGGER)) {
            handleOSCakeIssues(project, logger, OSCakeConfigParams.issuesLevel)
        }
        // transform result into json output
        val objectMapper = ObjectMapper()
        val outputFile = outputDir.resolve(reportFilename)
        outputFile.bufferedWriter().use {
            if (OSCakeConfigParams.prettyPrint)
                it.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(project))
            else
                it.write(objectMapper.writeValueAsString(project))
        }
        return listOf(outputFile)
    }

    /**
     * If the folder "native-scan-results" is empty, the raw data files from scanner are copied. The folder for
     * the raw data files is configured in ort.conf (scanner.options.ScanCode) as parameter "--json"
     */
    private fun prepareNativeScanResults(options: Map<String, Options>?) {
        // get folder name "rawDir" of scanner files from file "scan-result.yml"
        val scannerCommandLineParams = (options?.get("ScanCode")?.get("commandLine") ?: "").split(" ")
        val ind = scannerCommandLineParams.indexOfFirst { it == "--json" }
        require(ind > 0) { "The scanner was run without option --json, therefore no scan data exists!" }

        val scannerRaw = scannerCommandLineParams[ind + 1]
        val rawDir = File(scannerRaw).parent

        val rawDataExists = File(rawDir).exists() && File(rawDir).listFiles()!!.isNotEmpty()
        val ortScanResultsDir = File(OSCakeConfigParams.ortScanResultsDir!!)
        val scanDataExists = ortScanResultsDir.exists() && ortScanResultsDir.listFiles()!!.isNotEmpty()

        require(!(!rawDataExists && !scanDataExists)) {
                "No native-scan-results found! Check folder $rawDir or re-run the scanner once again!"
        }

        if (rawDataExists) {
            // copy raw data to native-scan-results
            if (!ortScanResultsDir.exists()) ortScanResultsDir.mkdir()
            File(rawDir).listFiles()?.forEach {
                var fileName = getOriginalFolderName(it)
                require(fileName != null) {
                    "Cannot identify file name in \"${it.name}\" in \"$rawDir\"! " +
                        "Clean up the folders \"$rawDir\" and ${ortScanResultsDir.name} and re-run the scanner first!"
                }

                fileName += "scan-results_ScanCode.json"
                if (ortScanResultsDir.resolve(fileName).exists()) logger.log(
                    "File $fileName (${it.name}) " +
                    "already exists and will be overwritten in ${OSCakeConfigParams.ortScanResultsDir}!",
                    Level.INFO,
                    phase = ProcessingPhase.PRE
                )

                it.copyTo(ortScanResultsDir.resolve(fileName), true)
            }
            // delete rawData
            File(rawDir).deleteRecursively()
            File(rawDir).mkdir()
            logger.log(
                "ScanCode raw data files were copied from $rawDir to ${OSCakeConfigParams.ortScanResultsDir}",
                Level.INFO,
                phase = ProcessingPhase.PRE
            )
        }
    }

    /**
     * Read the given file and extract the original folder name of the json file in "input" line
     * e.g. ".AppData\\Local\\Temp\\ort-ScanCode2604498589299189126\\Maven\\junit\\junit\\4.11"
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
                val ind = inps.indexOfFirst { it2 -> it2.startsWith("ort-ScanCode") }
                ret = ""
                for (n in ind + 1 until inps.size) {
                    ret += "${inps[n]}/"
                }
            }
        }
        return ret
    }

    /**
     * [reportScoreBelowThreshold] logs every license entry which has a score lower than the configured threshold
     */
    private fun reportScoreBelowThreshold(scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>) =
        scanDict.forEach { (pkg, fibMap) ->
            fibMap.forEach { (_, fib) ->
                fib.licenseTextEntries.filter { it.score <= OSCakeConfigParams.licenseScoreThreshold }
                    .forEach {
                        logger.log(
                            "Score <${it.score}> of license: ${it.license} in file \"${fib.path}\" is " +
                               "lower than the configured threshold of <${OSCakeConfigParams.licenseScoreThreshold}>!",
                            Level.WARN,
                            pkg,
                            phase = ProcessingPhase.PRE
                    )
                }
            }
        }

    /**
     * If a file contains more than one license text entry for a specific license and the difference of the
     * scores of the licenses is greater than 1, then the entry is removed - the one with the highest score remains.
     */
    private fun removeScoreBasedMultipleLicensesPerFile(
        scanDict: MutableMap<Identifier,
        MutableMap<String,
        FileInfoBlock>>
    ) = scanDict.forEach { (pkg, fibMap) ->
            fibMap.filter { it.value.licenseTextEntries.size > 1 }
                .forEach { (_, fib) ->
                val lteToRemove = mutableListOf<LicenseTextEntry>()
                // sort by score descending
                val sortedPairs = fib.licenseTextEntries.sortedBy { -it.score }.map { it to it.score }
                // take the one with the highest score
                var lastFound = sortedPairs[0]
                sortedPairs.forEach {
                    // if the difference between lastFound and the next in ranking is > 1, the next will be removed!
                    if (lastFound.second > it.second + 1) {
                        lteToRemove.add(it.first)
                        logger.log(
                            "License text finding for license \"${it.first.license}\" in " +
                                    "file: \"${fib.path}\" was removed (${it.first.startLine}/${it.first.endLine}), " +
                                    "due to multiple similar entries in the file!",
                            Level.DEBUG, pkg, fib.path, it.first, ScopeLevel.FILE, ProcessingPhase.PRE
                        )
                    } else lastFound = it
                }
                if (lteToRemove.isNotEmpty()) fib.licenseTextEntries.removeAll(lteToRemove)
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
    ): Project {
        val pkgMap = mutableMapOf<Identifier, Package>()
        val project = Project()
        // evaluated model contains a dependency tree of packages  with its corresponding levels (=depth in the tree)
        val evaluatedModel = EvaluatedModel.create(input)
        val tmpDirectory = kotlin.io.path.createTempDirectory(prefix = "oscake_").toFile()

        prepareProjects(project, input, scanDict, pkgMap)
        preparePackages(project, input, scanDict, pkgMap, evaluatedModel)
        processPackages(project, tmpDirectory, scanDict, input, pkgMap)

        project.complianceArtifactCollection.cid = input.ortResult.getProjects().first().id.toCoordinates()
        project.setDistributionType(evaluatedModel)
        project.setPackageType()
        project.complianceArtifactCollection.archivePath = "./" +
                input.ortResult.getProjects().first().id.name + ".zip"
        project.config = ConfigInfo(OSCakeConfigParams.osCakeConfigInformation)
        project.containsHiddenSections = OSCakeConfigParams.hideSections.isNotEmpty() &&
                project.hideSections(OSCakeConfigParams.hideSections, tmpDirectory)
        zipAndCleanUp(
            outputDir,
            tmpDirectory,
            project.complianceArtifactCollection.archivePath,
            logger,
            ProcessingPhase.POST
        )
        OSCakeConfigParams.onlyIncludePackages.filter { !it.value }.forEach { (identifier, _) ->
            logger.log(
                "packageRestrictions are enabled, but the package [$identifier] was not found",
                Level.WARN,
                identifier,
                phase = ProcessingPhase.PROCESS
            )
        }
        return project
    }

    private fun prepareProjects(
        project: Project,
        input: ReporterInput,
        scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
        pkgMap: MutableMap<Identifier, Package>
    ) {
        input.ortResult.analyzer?.result?.projects?.filter {
            OSCakeConfigParams.onlyIncludePackages.isEmpty() ||
                    (
                            OSCakeConfigParams.onlyIncludePackages.isNotEmpty() &&
                                    OSCakeConfigParams.onlyIncludePackages.contains(it.toPackage().id)
                            )
        }?.forEach {
            val pkg = it.toPackage()
            pkgMap[pkg.id] = it.toPackage()
            Pack(it.id, it.vcsProcessed.url, input.ortResult.repository.vcs.path)
                .apply {
                    declaredLicenses.add(it.declaredLicensesProcessed.spdxExpression.toString())
                    project.packs.add(this)
                    reuseCompliant = isREUSECompliant(this, scanDict)
                }
        }
    }

    private fun preparePackages(
        project: Project,
        input: ReporterInput,
        scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
        pkgMap: MutableMap<Identifier, Package>,
        evaluatedModel: EvaluatedModel
    ) {
        var cntLevelExcluded = 0
        input.ortResult.analyzer?.result?.packages?.filter { !input.ortResult.isExcluded(it.pkg.id) }?.filter {
            OSCakeConfigParams.onlyIncludePackages.isEmpty() ||
                    (
                            OSCakeConfigParams.onlyIncludePackages.isNotEmpty() &&
                                    OSCakeConfigParams.onlyIncludePackages.contains(it.pkg.id)
                            )
        }?.filter {
            OSCakeConfigParams.dependencyGranularity < Int.MAX_VALUE && OSCakeConfigParams.onlyIncludePackages.isEmpty()
            }
            ?.forEach {
                if (!treeLevelIncluded(
                        evaluatedModel.dependencyTrees,
                        OSCakeConfigParams.dependencyGranularity,
                        it.pkg.id
                    )
                ) {
                    cntLevelExcluded++
                    return@forEach
                }
                val pkg = it.pkg
                pkgMap[pkg.id] = it.pkg
                Pack(it.pkg.id, it.pkg.vcsProcessed.url, "")
                    .apply {
                        declaredLicenses.add(it.pkg.declaredLicensesProcessed.spdxExpression.toString())
                        project.packs.add(this)
                        reuseCompliant = isREUSECompliant(this, scanDict)
                    }
            }
        if (cntLevelExcluded > 0) logger.log(
            "Attention: 'dependency-granularity' is restricted to level:" +
            " ${OSCakeConfigParams.dependencyGranularity} via option! $cntLevelExcluded package(s) were excluded!",
            Level.INFO,
            phase = ProcessingPhase.PROCESS
        )
    }

    private fun processPackages(
        project: Project,
        tmpDirectory: File,
        scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
        input: ReporterInput,
        pkgMap: MutableMap<Identifier, Package>
    ) =
        project.packs.filter { scanDict.containsKey(it.id) }.forEach { pack ->
            // makes sure that the "pack" is also in the scanResults-file and not only in the
            // "native-scan-results" (=scanDict)
            input.ortResult.scanner?.results?.scanResults?.containsKey(pack.id)?.let {
                ModeSelector.getMode(pack, scanDict, input, pkgMap).apply {
                    // special case when packages with the same id but different provenances exist, only the
                    // first is taken
                    input.ortResult.scanner?.results?.scanResults?.get(pkgMap[pack.id]!!.id)?.let {
                        if (it.size > 1) {
                            logger.log(
                                "Package has more than one provenance! " +
                                        "Only the first one is taken into/from the sourcecode folder!",
                                Level.WARN,
                                pack.id,
                                phase = ProcessingPhase.PROCESS
                            )
                        }
                    }
                    val provenance = input.ortResult.scanner?.results?.scanResults!![pack.id]!!.first().provenance
                    downloadSourcesWhenNeeded(pack, scanDict, provenance)
                    fetchInfosFromScanDictionary(OSCakeConfigParams.sourceCodesDir, tmpDirectory, provenance)
                    postActivities(tmpDirectory)
                }
            }
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
    ): MutableMap<Identifier, MutableMap<String, FileInfoBlock>> {

        val scanDict = mutableMapOf<Identifier, MutableMap<String, FileInfoBlock>>()

        input.ortResult.scanner?.results?.scanResults?.
            filter { !input.ortResult.isExcluded(it.key) }?.
            filter {
                OSCakeConfigParams.onlyIncludePackages.isEmpty() ||
                    (
                        OSCakeConfigParams.onlyIncludePackages.isNotEmpty() &&
                        OSCakeConfigParams.onlyIncludePackages.contains(it.key)
                    )
            }?.
            forEach { (key, pp) ->
            if (pp.size > 1) logger.log(
                "Package has more than one provenance! Only the first one is taken!",
                Level.WARN,
                key,
                phase = ProcessingPhase.SCANRESULT
            )
            if (OSCakeConfigParams.onlyIncludePackages.isNotEmpty()) OSCakeConfigParams.onlyIncludePackages[key] = true
            pp.first().also { scanResult ->
                try {
                    val fileInfoBlockDict = HashMap<String, FileInfoBlock>()
                    val nsr = getNativeScanResultJson(
                        key,
                        OSCakeConfigParams.ortScanResultsDir
                    )

                    scanResult.summary.licenseFindings
                        .filter { !(it.license.toString() == "NOASSERTION" && OSCakeConfigParams.ignoreNOASSERTION) }
                        .filter {
                            !(
                            it.license.toString().startsWith("LicenseRef") && OSCakeConfigParams.ignoreLicenseRef
                            )
                        }
                        .filter {
                            !(
                                it.license.toString().startsWith("LicenseRef-scancode") &&
                                it.license.toString().contains("unknown") &&
                                OSCakeConfigParams.ignoreLicenseRefScancodeUnknown
                            )
                        }
                        .forEach {
                        val fileInfoBlock =
                            fileInfoBlockDict.getOrPut(it.location.path) { FileInfoBlock(it.location.path) }

                        LicenseTextEntry().apply {
                            license = it.license.toString()
                            isInstancedLicense = isInstancedLicense(input, it.license.toString())
                            startLine = it.location.startLine
                            endLine = it.location.endLine
                            score = if (it.score == null) 0.0 else it.score!!.toDouble()
                            if (license != "NOASSERTION")
                                combineNativeScanResults(fileInfoBlock.path, this, nsr)
                            else {
                                isLicenseNotice = true
                                isLicenseText = false
                            }
                            fileInfoBlock.licenseTextEntries.add(this)
                            license?.let { lic ->
                                if (lic.startsWith("LicenseRef")) logger.log(
                                    "LicenseReference found: \"$lic\" in File: \"${fileInfoBlock.path}\"",
                                    Level.WARN,
                                    key,
                                    phase = ProcessingPhase.PRE
                                )
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
                    logger.log(
                        "Native scan result was not found: ${fileNotFound.message}",
                        Level.ERROR,
                        key,
                        phase = ProcessingPhase.SCANRESULT
                    )
                }
            }
        }
        return scanDict
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
                            licenseTextEntry.endLine == license["end_line"].intValue() &&
                            licenseTextEntry.license == license["spdx_license_key"].toString().replace("\"", "")
                    ) {
                        licenseTextEntry.isLicenseText = license["matched_rule"]["is_license_text"].asBoolean()
                        licenseTextEntry.isLicenseNotice = !licenseTextEntry.isLicenseText
                        return
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
        if (OSCakeConfigParams.forceIncludePackages.contains(id)) {
            OSCakeConfigParams.forceIncludePackages[id] = true
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
    private fun isValidFile(map: Map<String, String>): Boolean =
        if (map["configFile"] != null) File(map["configFile"]!!).exists() &&
                File(map["configFile"]!!).isFile else false
}
