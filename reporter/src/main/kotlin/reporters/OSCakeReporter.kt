/*
 * Copyright (C) 2021 Deutsche Telekom AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import kotlin.collections.HashMap

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.CopyrightTextEntry
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.CurationManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.FileInfoBlock
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.LicenseTextEntry
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ModeSelector
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeConfiguration
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeRoot
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeWrapper
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Pack
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.REPORTER_LOGGER
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
    private lateinit var osCakeConfiguration: OSCakeConfiguration
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(REPORTER_LOGGER) }

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        // check configuration - cancel processing if necessary
        require(isValidFolder(options, "nativeScanResultsDir")) { "Value for 'nativeScanResultsDir' " +
                "is not a valid folder!\n Add -O OSCake=nativeScanResultsDir=... to the commandline" }
        require(isValidFolder(options, "sourceCodeDownloadDir")) { "Value for 'sourceCodeDownloadDir' is not a valid " +
                "folder!\n Add -O OSCake=sourceCodeDownloadDir=... to the commandline" }
        require(isValidFile(options, "configFile")) {
                "Value for 'OSCakeConf' is not a valid " +
                    "configuration file!\n Add -O OSCake=configFile=... to the commandline"
        }
        osCakeConfiguration = getOSCakeConfiguration(options["configFile"])
        if (osCakeConfiguration.curations?.get("enabled").toBoolean()) {
            require(isValidCurationFolder(osCakeConfiguration.curations?.get("fileStore"))) {
                "Invalid or missing config entry found for \"curations.filestore\" in oscake.conf"
            }
            require(isValidCurationFolder(osCakeConfiguration.curations?.get("directory"))) {
                "Invalid or missing config entry found for \"curations.directory\" in oscake.conf"
            }
        }
        // relay issues from ORT
        input.ortResult.analyzer?.result?.let {
            if (it.hasIssues)logger.log("Issue in ORT-ANALYZER - please check ORT-logfile or console output " +
                    "or scan_result.yml", Level.WARN)
        }
        input.ortResult.scanner?.results?.let {
            if (it.hasIssues) logger.log("Issue in ORT-SCANNER - please check ORT-logfile or console output " +
                    "or scan-result.yml", Level.WARN)
        }

        // start processing
        val scanDict = getNativeScanResults(input, options["nativeScanResultsDir"])
        val osc = ingestAnalyzerOutput(input, scanDict, outputDir, options["sourceCodeDownloadDir"])
        // transform result into json output
        val objectMapper = ObjectMapper()
        val outputFile = outputDir.resolve(reportFilename)
        outputFile.bufferedWriter().use {
            it.write(objectMapper.writeValueAsString(osc.project))
        }
        // process curations
        if (osCakeConfiguration.curations?.get("enabled").toBoolean()) CurationManager(osc.project,
            osCakeConfiguration, outputDir, reportFilename).manage()

        return listOf(outputFile)
    }

    /**
     * Ingest analyzer output:
     *      1. create an entry for each project and included package which should be handled
     *      2. create a temporary folder to hold the identified files to archive
     *      3. process the infos from the scanner
     */
    private fun ingestAnalyzerOutput(
        input: ReporterInput,
        scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
        outputDir: File,
        sourceCodeDir: String?
    ): OSCakeRoot {
        val osc = OSCakeRoot(input.ortResult.getProjects().first().id.toCoordinates().toString())

        // prepare projects and packages
        input.ortResult.analyzer?.result?.projects?.forEach {
            Pack(it.id, it.vcsProcessed.url, input.ortResult.repository.vcs.path)
                .apply {
                    declaredLicenses.add(it.declaredLicensesProcessed.spdxExpression.toString())
                    osc.project.packs.add(this)
                    reuseCompliant = isREUSECompliant(this, scanDict)
                }
        }
        input.ortResult.analyzer?.result?.packages?.filter { !input.ortResult.isExcluded(it.pkg.id) }?.forEach {
            Pack(it.pkg.id, it.pkg.vcsProcessed.url, "")
                .apply {
                    declaredLicenses.add(it.pkg.declaredLicensesProcessed.spdxExpression.toString())
                    osc.project.packs.add(this)
                    reuseCompliant = isREUSECompliant(this, scanDict)
                }
        }

        val tmpDirectory = kotlin.io.path.createTempDirectory(prefix = "oscake_").toFile()

        osc.project.packs.filter { scanDict.containsKey(it.id) }.forEach { pack ->
            // makes sure that the "pack" is also in the scanResults-file and not only in the
            // "native-scan-results" (=scanDict)
            input.ortResult.scanner?.results?.scanResults?.any { it.id == pack.id }?.let {
                ModeSelector.getMode(pack, scanDict, osCakeConfiguration, input).apply {
                    fetchInfosFromScanDictionary(sourceCodeDir, tmpDirectory)
                    postActivities()
                }
            }
        }

        osc.project.complianceArtifactCollection.archivePath = "./" +
                input.ortResult.getProjects().first().id.name + ".zip"

        zipAndCleanUp(outputDir, tmpDirectory, osc.project.complianceArtifactCollection.archivePath)

        if (OSCakeLoggerManager.hasLogger(REPORTER_LOGGER)) handleOSCakeIssues(osc.project, logger)

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
    private fun getNativeScanResults(input: ReporterInput, nativeScanResultsDir: String?):
            MutableMap<Identifier, MutableMap<String, FileInfoBlock>> {
        val scanDict = mutableMapOf<Identifier, MutableMap<String, FileInfoBlock>>()

        input.ortResult.scanner?.results?.scanResults?.filter { !input.ortResult.isExcluded(it.id) }?.forEach { pp ->
                pp.results.forEach {
                    val fileInfoBlockDict = HashMap<String, FileInfoBlock>()
                    val nsr = getNativeScanResultJson(pp, nativeScanResultsDir)

                    it.summary.licenseFindings.forEach {
                        val fileInfoBlock =
                            fileInfoBlockDict.getOrPut(it.location.path) { FileInfoBlock(it.location.path) }

                        LicenseTextEntry().apply {
                            license = it.license.toString()
                            if (it.license.toString().startsWith("LicenseRef-")) {
                                logger.log("Changed <${it.license}> to <NOASSERTION> in package: " +
                                        "${pp.id.name} - ${it.location.path}", Level.INFO, pp.id, fileInfoBlock.path)
                                license = "NOASSERTION"
                            }
                            isInstancedLicense = isInstancedLicense(input, it.license.toString())
                            startLine = it.location.startLine
                            endLine = it.location.endLine
                            combineNativeScanResults(fileInfoBlock.path, this, nsr)
                            fileInfoBlock.licenseTextEntries.add(this)
                        }
                    }
                    it.summary.copyrightFindings.forEach {
                        fileInfoBlockDict.getOrPut(it.location.path) { FileInfoBlock(it.location.path)
                        }.apply {
                            copyrightTextEntries.add(CopyrightTextEntry(it.location.startLine,
                                it.location.endLine, it.statement))
                        }
                    }
                    scanDict[pp.id] = fileInfoBlockDict
                }
        }
        return scanDict
    }

    private fun getNativeScanResultJson(
        entity: ScanResultContainer,
        nativeScanResultsDir: String?
    ): JsonNode {
        val subfolder = entity.id.toPath()
        val filePath = "$nativeScanResultsDir/$subfolder/scan-results_ScanCode.json"

        val scanFile: File = File(filePath)
        if (!scanFile.exists()) {
            throw java.io.FileNotFoundException(
                "Recheck the path of option <-i> and the option for <nativeScanResultsDir>")
        }
        var node: JsonNode = EMPTY_JSON_NODE
        if (scanFile.isFile && scanFile.length() > 0L) {
            node = jsonMapper.readTree(scanFile)
        }

        return node
    }

    /**
     * Searches for specific infos (e.g. flag: is_license_text) in native scan results (represented in json)
     * based on "path", "start_line", "end_line" and updates this information in the licenseTextEntry
     *
     * @param path
     * @param licenseTextEntry
     * @param node
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

    private fun getOSCakeConfiguration(fileName: String?): OSCakeConfiguration {
        val config = ConfigLoader.Builder()
            .addSource(PropertySource.file(File(fileName)))
            .build()
            .loadConfig<OSCakeWrapper>()

        return config.map { it.OSCake }.getOrElse { failure ->
            throw IllegalArgumentException(
                "Failed to load configuration from ${failure.description()}")
        }
    }

    // checks if the value of the optionName in map is a valid directory
    internal fun isValidFolder(map: Map<String, String>, optionName: String): Boolean =
        if (map[optionName] != null) File(map[optionName]).exists() && File(map[optionName]).isDirectory() else false

    // checks if the value of the optionName in map is a valid file
    internal fun isValidFile(map: Map<String, String>, optionName: String): Boolean =
        if (map[optionName] != null) File(map[optionName]).exists() && File(map[optionName]).isFile() else false

    internal fun isValidCurationFolder(dir: String?): Boolean =
        !(dir == null || !File(dir).exists() || !File(dir).isDirectory())
}
