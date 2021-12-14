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

import kotlin.io.path.createTempDirectory

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.deduplicator.PackDeduplicator
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*
import org.ossreviewtoolkit.utils.unpackZip

/**
 * The [OSCakeDeduplicator] deduplicates licenses and copyrights in all scopes
 */
class OSCakeDeduplicator(private val config: OSCakeConfiguration, private val osccFile: File) {

    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(DEDUPLICATION_LOGGER) }

    /**
     * The method [execute] reads the oscc file (json) into the data model and unpacks the referenced archive file. The
     * contained packages are deduplicated and the file references in the model are checked against the
     * zip archive. The resulting model is written into the output and the archived files are packed and stored
     * as a zip file.
     */
    fun execute() {
        val reportFile = File(osccFile.parent).resolve("$DEDUPLICATION_FILE_PREFIX${osccFile.name}")
        val osc = osccToJson(osccFile, logger, ProcessingPhase.DEDUPLICATION)
        val archiveDir = createTempDirectory(prefix = "oscakeDed_").toFile().apply {
            File(osccFile.parent, osc.project.complianceArtifactCollection.archivePath).unpackZip(this)
        }
        osc.project.packs.forEach {
            PackDeduplicator(it, archiveDir).deduplicate()
        }
        val archivePath = File(osc.project.complianceArtifactCollection.archivePath)
        osc.project.complianceArtifactCollection.archivePath = "./$DEDUPLICATION_FILE_PREFIX${archivePath.name}"

        var rc = compareLTIAwithArchive(osc.project, archiveDir, logger, ProcessingPhase.DEDUPLICATION)
        rc = rc || jsonToOscc(osc, reportFile, logger, ProcessingPhase.DEDUPLICATION)
        rc = rc || zipAndCleanUp(File(osccFile.parent), archiveDir,
            osc.project.complianceArtifactCollection.archivePath, logger, ProcessingPhase.DEDUPLICATION)
        if (rc) {
            logger.log("Deduplicator terminated with errors!", Level.ERROR, phase = ProcessingPhase.DEDUPLICATION)
        } else {
            logger.log(
                "Deduplicator terminated successfully! Result is written to: $reportFile", Level.INFO,
                phase = ProcessingPhase.DEDUPLICATION
            )
        }
    }
}
