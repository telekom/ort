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

package org.ossreviewtoolkit.oscake.curator

import java.io.File

import kotlin.io.path.createTempDirectory
import kotlin.system.exitProcess

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.CURATION_AUTHOR
import org.ossreviewtoolkit.oscake.CURATION_FILE_SUFFIX
import org.ossreviewtoolkit.oscake.CURATION_LOGGER
import org.ossreviewtoolkit.oscake.CURATION_VERSION
import org.ossreviewtoolkit.oscake.packageModifierMap
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*
import org.ossreviewtoolkit.utils.unpackZip

/**
 * The [CurationManager] handles the entire curation process: reads and analyzes the curation files,
 * prepares the list of packages (passed by the reporter), applies the curations and writes the results into the output
 * files - one oscc compliant file in json format and a zip-archive containing the license texts.
 */
internal class CurationManager(
    /**
     * The [project] contains the processed output from the OSCakeReporter - specifically a list of packages.
     */
    private val project: Project,
    /**
     * The generated files are stored in the folder [outputDir]
     */
    val outputDir: File,
    /**
     * The name of the reporter's output file which is extended by the [CurationManager]
     */
    private val reportFilename: String,
    /**
     * Configuration in ort.conf
     */
    val config: OSCakeConfiguration,
    /**
     * option which indicates that the Warnings on root level are removed
     */
    private val ignoreRootWarnings: Boolean
    ) {

    /**
     * If curations have to be applied, the reporter's zip-archive is unpacked into this temporary folder.
     */
    private val archiveDir: File by lazy {
        createTempDirectory(prefix = "oscakeCur_").toFile().apply {
            File(outputDir, project.complianceArtifactCollection.archivePath).unpackZip(this)
        }
    }

    /**
     * The [curationProvider] contains a list of [PackageCuration]s to be applied.
     */
    private var curationProvider = CurationProvider(File(config.curator?.directory!!),
        File(config.curator?.fileStore!!))

    /**
     * The [logger] is only initialized, if there is something to log.
     */
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(CURATION_LOGGER) }

    /**
     * The method takes the reporter's output, checks and updates the reported "hasIssues", prioritizes the
     * package modifiers, applies the curations, reports emerged issues and finally, writes the output files.
     */
    internal fun manage() {
        // 0. Reset hasIssues = false - will be newly set at the end of the process
        project.hasIssues = false
        project.packs.forEach { pack ->
            pack.hasIssues = false
            pack.defaultLicensings.forEach {
                it.hasIssues = false
            }
            pack.dirLicensings.forEach { dirLicensing ->
                dirLicensing.licenses.forEach {
                    it.hasIssues = false
                }
            }
        }
        // 1. handle packageModifiers
        val orderByModifier = packageModifierMap.keys.withIndex().associate { it.value to it.index }
        curationProvider.packageCurations.sortedBy { orderByModifier[it.packageModifier] }.forEach { packageCuration ->
            when (packageCuration.packageModifier) {
                "insert" -> if (project.packs.none { it.id == packageCuration.id }) {
                    eliminateIssueFromRoot(project.issueList, packageCuration.id.toCoordinates())
                    Pack(packageCuration.id, packageCuration.repository ?: "", packageCuration.packageRoot ?: "")
                        .apply {
                            project.packs.add(this)
                            reuseCompliant = checkReuseCompliance(this, packageCuration)
                        }
                    } else {
                        logger.log("Package: \"${packageCuration.id}\" already exists - no duplication!",
                            Level.INFO, phase = ProcessingPhase.CURATION
                        )
                    }
                "delete" -> {
                    eliminateIssueFromRoot(project.issueList, packageCuration.id.toCoordinates())
                    deletePackage(packageCuration, archiveDir)
                }
            }
        }

        // 2. curate each package regarding the "modifier" - insert, delete, update
        // and "packageModifier" - update, insert, delete
        val scopePatterns = project.config?.reporter?.configFile?.scopePatterns ?: emptyList()
        val copyrightScopePatterns = scopePatterns +
                (project.config?.reporter?.configFile?.copyrightScopePatterns ?: emptyList())
        project.packs.forEach {
            curationProvider.getCurationFor(it.id)?.curate(it, archiveDir,
                File(config.curator?.fileStore!!), scopePatterns, copyrightScopePatterns)
        }

        // 3. report [OSCakeIssue]s
        if (OSCakeLoggerManager.hasLogger(CURATION_LOGGER)) handleOSCakeIssues(project, logger,
            config.curator?.issueLevel ?: -1)

        // 4. eliminate root level warnings (only warnings from reporter) when option is set
        if (ignoreRootWarnings) eliminateRootWarnings()

        // 5. take care of issue level settings to create the correct output format
        takeCareOfIssueLevel()

        // 6. generate .zip and .oscc files
        createResultingFiles()
    }

    /**
     * Remove warnings from project issueList with format: "Wxx" - theses are warnings created by the reporter and
     * can be overridden manually by option
     */
    private fun eliminateRootWarnings() {
        val pattern = "W\\d\\d".toRegex()
        val idList = project.issueList.warnings.filter { pattern.matches(it.id) }.map { it.id }
        project.issueList.warnings.removeAll { idList.contains(it.id) }
        propagateHasIssues(project)
    }

    /**
     * Clear all lists in issueLists (project, package, ...) depending on the issueLevel - set in ort.conf, in order
     * to produce the correct output
     */
    private fun takeCareOfIssueLevel() {
        eliminateIssuesFromLevel(project.issueList)
        project.packs.forEach { pack ->
            eliminateIssuesFromLevel(pack.issueList)
            pack.defaultLicensings.forEach {
                eliminateIssuesFromLevel(it.issueList)
            }
            pack.dirLicensings.forEach { dirLicensing ->
                dirLicensing.licenses.forEach {
                    eliminateIssuesFromLevel(it.issueList)
                }
            }
        }
    }

    /**
     * Clear the [issueList]s depending on issue level
     */
    private fun eliminateIssuesFromLevel(issueList: IssueList) {
        val issLevel = config.curator?.issueLevel ?: -1
        if (issLevel == -1) {
            issueList.infos.clear()
            issueList.warnings.clear()
            issueList.errors.clear()
        }
        if (issLevel == 0) {
            issueList.infos.clear()
            issueList.warnings.clear()
        }
        if (issLevel == 1) issueList.infos.clear()
    }

    /**
     * Remove issues with specific format: e.g. "W_Maven:org.yaml:snakeyaml:1.28"
     */
    private fun eliminateIssueFromRoot(issueList: IssueList, idStr: String) {
        issueList.infos.removeAll { it.id == "I_$idStr" }
        issueList.warnings.removeAll { it.id == "W_$idStr" }
        issueList.errors.removeAll { it.id == "E_$idStr" }
    }

    private fun checkReuseCompliance(pack: Pack, packageCuration: PackageCuration): Boolean =
        packageCuration.curations?.any {
            it.fileScope.startsWith(getLicensesFolderPrefix(pack.packageRoot))
        } ?: false

    /**
     * The method writes the output file in oscc format (json file named  "..._curated.oscc") and creates a zip file
     * containing license text files named "..._curated.zip"
     */
    private fun createResultingFiles() {
        val reportFile = File(File(reportFilename).parent).resolve(extendFilename(File(reportFilename),
            CURATION_FILE_SUFFIX))
        val sourceZipFileName = File(stripRelativePathIndicators(project.complianceArtifactCollection.archivePath))
        val newZipFileName = extendFilename(sourceZipFileName, CURATION_FILE_SUFFIX)

        var rc = false
        project.complianceArtifactCollection.archivePath =
            File(project.complianceArtifactCollection.archivePath).parentFile.name + "/" + newZipFileName
        project.complianceArtifactCollection.author = CURATION_AUTHOR
        project.complianceArtifactCollection.release = CURATION_VERSION

        if (archiveDir.exists()) {
            rc = rc || compareLTIAwithArchive(project, archiveDir, logger, ProcessingPhase.CURATION)
            rc = rc || zipAndCleanUp(outputDir, archiveDir, newZipFileName, logger, ProcessingPhase.CURATION)
        } else {
            val targetFile = File(outputDir.path, newZipFileName)
            File(outputDir, sourceZipFileName.name).copyTo(targetFile, true)
        }
        rc = rc || modelToOscc(project, reportFile, logger, ProcessingPhase.CURATION)

        rc = rc || CurationProvider.errors
        if (!rc) {
            logger.log("Curator terminated successfully! Result is written to: ${reportFile.name}", Level.INFO,
                phase = ProcessingPhase.CURATION)
        } else {
            logger.log("Curator terminated with errors!", Level.ERROR, phase = ProcessingPhase.CURATION)
            exitProcess(4)
        }
    }

    private fun deletePackage(packageCuration: PackageCuration, archiveDir: File) {
        val packsToDelete = mutableListOf<Pack>()
        project.packs.filter { curationProvider.getCurationFor(it.id) == packageCuration }.forEach { pack ->
            pack.fileLicensings.forEach { fileLicensing ->
                deleteFromArchive(fileLicensing.fileContentInArchive, archiveDir)
                fileLicensing.licenses.forEach {
                    deleteFromArchive(it.licenseTextInArchive, archiveDir)
                }
            }
            packsToDelete.add(pack)
        }
        project.packs.removeAll(packsToDelete)
    }
}
