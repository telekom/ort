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
class OSCakeCurator(private val config: OSCakeConfiguration, private val commandLineParams: Map<String, String>) {
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(CURATION_LOGGER) }

    /**
     * Generates the json from file and starts the curation process
     */
    fun execute() {
        val osccFile = File(commandLineParams["osccFile"]!!)
        val outputDir = File(commandLineParams["outputDir"]!!)
        val ignoreRootWarnings = commandLineParams.getOrDefault("ignoreRootWarnings", "false").toBoolean()

        val osc = osccToModel(osccFile, logger, ProcessingPhase.CURATION)

        if (osc.project.containsHiddenSections == true) {
            logger.log("The file \"${osccFile.name}\" cannot be processed, because some sections are missing!" +
                    " (maybe it was created with config option \"hideSections\")", Level.ERROR,
                phase = ProcessingPhase.CURATION)
            exitProcess(12)
        }
        if (osc.project.complianceArtifactCollection.author == DEDUPLICATION_AUTHOR) {
            logger.log("The file \"${osccFile.name}\" cannot be processed, because it was already deduplicated" +
                    " in a former run!", Level.ERROR, phase = ProcessingPhase.CURATION)
            exitProcess(10)
        }
        // A merged oscc-file cannot be curated because there is no config information anymore (scopePatterns
        // are missing); additionally the tag "mergedIDs" contains a list of merged ComplianceArtifactCollection
        if (osc.project.complianceArtifactCollection.mergedIds.isNotEmpty()) {
            logger.log(
                "The given project is a merged project and cannot be curated anymore!",
                Level.ERROR, phase = ProcessingPhase.CURATION
            )
            exitProcess(11)
        }
        addParamsToConfig(config, osc, commandLineParams, this)

        CurationManager(osc.project, outputDir, osccFile.absolutePath, config, ignoreRootWarnings).manage()
    }
}
