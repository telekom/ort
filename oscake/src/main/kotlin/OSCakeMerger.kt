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

package org.ossreviewtoolkit.oscake

import java.io.File
import java.time.LocalDateTime

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.merger.ProjectProvider
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.ProcessingPhase

/**
 * The [OSCakeMerger] combines different *.oscc files (only with hasIssues = false) into a new
 * ComplianceArtifactCollection. The referenced license files are copied from the origin to the new zip-file.
 * In order to create unique file references the path to the files gets a hash code as a prefix
 */
class OSCakeMerger(
    private val config: OSCakeConfiguration,
    private val cid: String,
    private val inputDir: File,
    private val outputFile: File,
    private val commandLineParams: Map<String, String>
) {
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(MERGER_LOGGER) }
    private val identifier = Identifier(cid)

    /**
     * walks through the inputDirectory, takes every *.oscc file and copies the ComplianceArtifactPackages
     * into the merged file.
     */
    fun execute() {
        val archiveFileRelativeName = "./${identifier.name}.zip"
        val archiveFile = outputFile.parentFile.resolve(archiveFileRelativeName)
        var inputFileCounter = 0

        // merge all packages into new project
        val cac = ComplianceArtifactCollection(
            cid,
            MERGER_AUTHOR,
            MERGER_VERSION,
            LocalDateTime.now().toString(),
            archivePath = archiveFileRelativeName
        )
        val mergedProject = Project.initialize(cac, archiveFile, logger)

        inputDir.walkTopDown().filter { it.isFile && it.extension == "oscc" }.forEach { file ->
            ProjectProvider.getProject(file.absoluteFile)?.let { project ->
                if (mergedProject.merge(project, file, DEDUPLICATION_AUTHOR)) {
                    cac.mergedIds.add(project.complianceArtifactCollection.cid)
                    if (project.complianceArtifactCollection.mergedIds.isNotEmpty()) {
                        cac.mergedIds.addAll(project.complianceArtifactCollection.mergedIds)
                    }
                    inputFileCounter++
                    val mergedFile = file.relativeToOrNull(inputDir) ?: ""
                    logger.log("File: <$mergedFile> successfully merged!", Level.INFO, phase = ProcessingPhase.MERGING)
                }
            }
        }
        Project.terminateArchiveHandling()

        ConfigInfo().also {
            it.merger = ConfigBlockInfo(commandLineParams, mapOf("prettyPrint" to "${config.prettyPrint ?: false}"))
            mergedProject.config = it
        }

        val rc = mergedProject.modelToOscc(outputFile, logger, ProcessingPhase.MERGING, config.prettyPrint ?: false)

        if (rc) logger.log(
                    "Writing of output file does not succeed!",
                    Level.ERROR,
                    phase = ProcessingPhase.MERGING
                )
        else logger.log(
                "Number of processed oscc-input files: $inputFileCounter - successfully merged " +
                        "into: \"${outputFile.name}\"",
                    Level.INFO,
                    phase = ProcessingPhase.MERGING
                )
    }
}
