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

import kotlin.system.exitProcess

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.curator.CurationManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.osccToModel

/**
 * The [OSCakeCurator] provides a mechanism to curate issues (WARNINGS & ERRORS) in an *.oscc file. Additionally,
 * ComplianceArtifactPackages can be added and/or deleted.
 */
class OSCakeCurator(private val config: OSCakeConfiguration, private val osccFile: File,
                     private val outputDir: File, private val ignoreRootWarnings: Boolean) {

    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(CURATION_LOGGER) }

    /**
     * Generates the json from file and starts the curation process
     */
    fun execute() {
        val osc = osccToModel(osccFile, logger, ProcessingPhase.CURATION)
        if (osc.project.complianceArtifactCollection.author == DEDUPLICATION_AUTHOR) {
            logger.log("The file \"${osccFile.name}\" cannot be processed, because it was already deduplicated" +
                    " in a former run!", Level.ERROR, phase = ProcessingPhase.DEDUPLICATION)
            exitProcess(10)
        }
        CurationManager(osc.project, outputDir, osccFile.absolutePath, config, ignoreRootWarnings).manage()
    }
}
