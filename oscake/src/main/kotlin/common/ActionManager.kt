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

package org.ossreviewtoolkit.oscake.common

import java.io.File
import java.time.LocalDateTime

import kotlin.system.exitProcess

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.curator.CurationManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.IssueList
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Project
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.compareLTIAwithArchive
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.extendFilename
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.modelToOscc
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.propagateHasIssues
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.stripRelativePathIndicators
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.zipAndCleanUp

/**
 * The class [ActionManager] is a skeleton (base class) of common properties and functions for the apps "curation",
 * "resolver", etc.
 */
open class ActionManager(
    /**
      * The [project] contains the processed output from the OSCakeReporter - specifically a list of packages.
      */
    open val project: Project,
    /**
      * The generated files are stored in the folder [outputDir]
      */
    open val outputDir: File,
    /**
      * The name of the reporter's output file which is extended by the [CurationManager]
      */
    open val reportFilename: String,
    /**
      * Configuration in ort.conf
      */
    open val config: OSCakeConfiguration,
    /**
     * [actionInfo] contains information about the specific action ("curate", "resolve", etc.)
     */
    private val actionInfo: ActionInfo,
    /**
     * [commandLineParams] contains a map of passed commandline parameters
     */
    open val commandLineParams: Map<String, String>
    ) {
    /**
     * The [logger] is only initialized, if there is something to log.
     */
    val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(actionInfo.loggerName) }

    /**
     * the method sets all hasIssues properties in the project to false
     */
    fun resetIssues() {
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
    }

    /**
     * The method writes the output file in oscc format (json file named  "..._curated.oscc") and creates a zip file
     * containing license text files named "..._curated.zip"
     */
    fun createResultingFiles(archiveDir: File) {
        val reportFile = File(File(reportFilename).parent).resolve(
            extendFilename(File(reportFilename),
            actionInfo.suffix
            )
        )
        val sourceZipFileName = File(stripRelativePathIndicators(project.complianceArtifactCollection.archivePath))
        val newZipFileName = extendFilename(sourceZipFileName, actionInfo.suffix)

        var rc = false
        if (archiveDir.exists()) {
            rc = rc || compareLTIAwithArchive(project, archiveDir, logger, actionInfo.phase)
            rc = rc || zipAndCleanUp(outputDir, archiveDir, newZipFileName, logger, actionInfo.phase)
        } else {
            val targetFile = File(outputDir.path, newZipFileName)
            File(outputDir, sourceZipFileName.name).copyTo(targetFile, true)
        }
        project.complianceArtifactCollection.archivePath =
            File(project.complianceArtifactCollection.archivePath).parentFile.name + "/" + newZipFileName
        project.complianceArtifactCollection.author = actionInfo.author
        project.complianceArtifactCollection.date = LocalDateTime.now().toString()
        project.complianceArtifactCollection.release = actionInfo.release

        rc = rc || modelToOscc(project, reportFile, logger, actionInfo.phase)

        rc = rc || ActionProvider.errors
        if (!rc) {
            logger.log("${actionInfo.actor} terminated successfully! Result is written to: ${reportFile.name}",
                Level.INFO, phase = actionInfo.phase)
        } else {
            logger.log("${actionInfo.actor} terminated with errors!", Level.ERROR, phase = actionInfo.phase)
            exitProcess(4)
        }
    }

    /**
     * Clear all lists in issueLists (project, package, ...) depending on the issueLevel - set in ort.conf, in order
     * to produce the correct output
     */
    fun takeCareOfIssueLevel() {
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
        val issLevel = actionInfo.issueLevel
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
    fun eliminateIssueFromRoot(issueList: IssueList, idStr: String) {
        issueList.infos.removeAll { it.id == "I_$idStr" }
        issueList.warnings.removeAll { it.id == "W_$idStr" }
        issueList.errors.removeAll { it.id == "E_$idStr" }
    }

    /**
     * Remove warnings from project issueList with format: "Wxx" - these are warnings created by the reporter and
     * can be overridden manually by option
     */
    fun eliminateRootWarnings() {
        val pattern = "W\\d\\d".toRegex()
        val idList = project.issueList.warnings.filter { pattern.matches(it.id) }.map { it.id }
        project.issueList.warnings.removeAll { idList.contains(it.id) }
        propagateHasIssues(project)
    }
}
