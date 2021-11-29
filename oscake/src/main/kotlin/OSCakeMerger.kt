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

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.Level
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.oscake.merger.ProjectProvider
import java.io.File
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*

class OSCakeMerger (private val cid: String, private val inputDir: File, private val outputFile: File){

    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(MERGER_LOGGER) }
    private val identifier = Identifier(cid)

    /**
     * walks through the inputDirectory, takes every *.oscc file and copies the ComplianceArtifactPackages
     * into the merged file.
     */
    fun execute() {

        val archiveFileRelativeName = "./${identifier.name}.$OSCAKE_MERGER_ARCHIVE_TYPE"
        val archiveFile = outputFile.parentFile.resolve(archiveFileRelativeName)
        var inputFileCounter = 0

        // merge all packages into new project

        val cac = ComplianceArtifactCollection(cid, OSCAKE_MERGER_AUTHOR, MERGER_VERSION,
            archivePath = archiveFileRelativeName)
        val mergedProject = Project.initialize(cac, archiveFile, logger)

        inputDir.walkTopDown().filter { it.isFile && it.extension == "oscc" }.forEach { file ->
            ProjectProvider.getProject(file.absoluteFile)?.let { project ->
                if (mergedProject.merge(project, file)) {
                    cac.mergedIds.add(project.complianceArtifactCollection.cid)
                    if (project.complianceArtifactCollection.mergedIds.isNotEmpty())
                        cac.mergedIds.addAll(project.complianceArtifactCollection.mergedIds)
                    inputFileCounter++
                    val mergedFile = file.relativeToOrNull(inputDir) ?: ""
                    logger.log("File: <$mergedFile> successfully merged!", Level.INFO, phase = ProcessingPhase.MERGING)
                }
            }
        }
        Project.terminateArchiveHandling()

        val objectMapper = ObjectMapper()
        outputFile.bufferedWriter().use {
            it.write(objectMapper.writeValueAsString(mergedProject))
        }

        logger.log("Number of processed oscc-input files: $inputFileCounter", Level.INFO,
            phase = ProcessingPhase.MERGING)
    }
}
