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

import kotlin.io.path.createTempDirectory
import kotlin.system.exitProcess

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.deduplicator.PackDeduplicator
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*
import org.ossreviewtoolkit.utils.common.unpackZip

/**
 * The [OSCakeDeduplicator] deduplicates licenses and copyrights in all scopes
 */
class OSCakeDeduplicator(
    private val config: OSCakeConfiguration,
    private val osccFile: File,
    private val commandLineParams: Map<String, String>
) {
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(DEDUPLICATION_LOGGER) }

    /**
     * The method [execute] reads the oscc file (json) into the data model and unpacks the referenced archive file. The
     * contained packages are deduplicated and the file references in the model are checked against the
     * zip archive. The resulting model is written into the output and the archived files are packed and stored
     * as a zip file.
     */
    fun execute() {
        require(config.deduplicator != null) { "No \"deduplicator\" section found in ort.conf" }
        val reportFile = File(osccFile.parent).resolve(extendFilename(File(osccFile.name), DEDUPLICATION_FILE_SUFFIX))
        val project = Project.osccToModel(osccFile, logger, ProcessingPhase.DEDUPLICATION)

        project.isProcessingAllowed(logger, osccFile, listOf(DEDUPLICATION_AUTHOR, MERGER_AUTHOR))

        val archiveDir = createTempDirectory(prefix = "oscakeDed_").toFile().apply {
            File(osccFile.parent, project.complianceArtifactCollection.archivePath).unpackZip(this)
        }

        project.config?.let { configInfo ->
            addParamsToConfig(config, commandLineParams, this)?.let {
                configInfo.deduplicator = it
            }
        }

        project.packs.forEach {
            var process = true
            if (config.deduplicator?.processPackagesWithIssues == true) {
                if (it.hasIssues) logger.log(
                    "Package will be deduplicated, although \"hasIssues=true\"",
                    Level.INFO,
                    it.id,
                    phase = ProcessingPhase.DEDUPLICATION
                )
            } else {
                if (it.hasIssues) {
                    logger.log(
                        "Package is not deduplicated, because \"hasIssues=true\"",
                        Level.INFO,
                        it.id,
                        phase = ProcessingPhase.DEDUPLICATION
                    )
                    process = false
                }
            }
            if (it.reuseCompliant) {
                logger.log(
                    "Package is REUSE compliant and is not deduplicated!",
                    Level.INFO,
                    it.id,
                    phase = ProcessingPhase.DEDUPLICATION
                )
                process = false
            }
            if (process) PackDeduplicator(it, archiveDir, config).deduplicate()
        }

        val sourceZipFileName = File(stripRelativePathIndicators(project.complianceArtifactCollection.archivePath))
        val newZipFileName = extendFilename(sourceZipFileName, DEDUPLICATION_FILE_SUFFIX)

        project.complianceArtifactCollection.archivePath =
                File(project.complianceArtifactCollection.archivePath).parentFile.name + "/" + newZipFileName
        project.complianceArtifactCollection.author = DEDUPLICATION_AUTHOR
        project.complianceArtifactCollection.date = LocalDateTime.now().toString()
        project.complianceArtifactCollection.release = DEDUPLICATION_VERSION

        if (config.deduplicator?.hideSections?.isNotEmpty() == true) {
            if (project.hideSections(config.deduplicator!!.hideSections ?: emptyList(), archiveDir)) {
                project.containsHiddenSections = true
            }
        }
        if (config.deduplicator?.createUnifiedCopyrights == true && project.packs.any { !it.reuseCompliant }) {
            project.containsHiddenSections = true
        }

        var rc = compareLTIAwithArchive(project, archiveDir, logger, ProcessingPhase.DEDUPLICATION)
        rc = rc || project.modelToOscc(reportFile, logger, ProcessingPhase.DEDUPLICATION, config.prettyPrint ?: false)
        rc = rc || zipAndCleanUp(
            File(osccFile.parent),
            archiveDir,
            project.complianceArtifactCollection.archivePath,
            logger,
            ProcessingPhase.DEDUPLICATION
        )
        if (rc) {
            logger.log("Deduplicator terminated with errors!", Level.ERROR, phase = ProcessingPhase.DEDUPLICATION)
            exitProcess(3)
        } else {
            logger.log(
                "Deduplicator terminated successfully! Result is written to: $reportFile", Level.INFO,
                phase = ProcessingPhase.DEDUPLICATION
            )
        }
    }
}
