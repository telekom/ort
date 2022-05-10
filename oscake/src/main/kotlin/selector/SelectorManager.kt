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

package org.ossreviewtoolkit.oscake.selector

import java.io.File

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.SELECTOR_LOGGER
import org.ossreviewtoolkit.oscake.common.ActionInfo
import org.ossreviewtoolkit.oscake.common.ActionManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.CompoundOrLicense
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeConfigParams
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Project
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.handleOSCakeIssues

/**
 * The [SelectorManager] handles the entire selection process: reads and analyzes the selector files,
 * prepares the list of packages (passed by the reporter), applies the selector actions and writes the results into
 * the output files - one oscc compliant file in json format and a zip-archive containing the license texts.
 */
internal class SelectorManager(
    /**
    * The [project] contains the processed output from the OSCakeReporter - specifically a list of packages.
    */
    override val project: Project,
    /**
    * The generated files are stored in the folder [outputDir]
    */
    override val outputDir: File,
    /**
    * The name of the reporter's output file which is extended by the [SelectorManager]
    */
    override val reportFilename: String,
    /**
    * Configuration in ort.conf
    */
    override val config: OSCakeConfiguration,
    /**
     * Map of passed commandline parameters
     */
    override val commandLineParams: Map<String, String>

) : ActionManager(
    project,
    outputDir,
    reportFilename,
    config,
    ActionInfo.selector(config.selector?.issueLevel ?: -1),
    commandLineParams
) {
    /**
     * The [selectorProvider] contains a list of [SelectorPackage]s to be applied.
     */
    private var selectorProvider = SelectorProvider(File(config.selector?.directory!!))

    /**
     * The method takes the reporter's output, checks and updates the reported "hasIssues", applies the
     * selector actions, reports emerged issues and finally, writes the output files.
     */
    internal fun manage() {
        // 1. Process resolver-package if it's valid, applicable and pack is not reuse-compliant
        OSCakeConfigParams.setParamsFromProject(project)
        project.packs.forEach {
            selectorProvider.getActionFor(it.id, true)?.process(it, archiveDir, logger)
        }
        // 2. Check if compound license and no originalLicense is set --> no resolver package exists
        project.packs.filter { !it.reuseCompliant }.forEach { pack ->
            pack.fileLicensings.forEach { fileLicensing ->
                fileLicensing.licenses.forEach {
                    if (CompoundOrLicense(it.license).isCompound && it.originalLicenses == null)
                        logger.log(
                            "Compound license \"${it.license}\": no selector was found for this license!",
                            Level.WARN,
                            pack.id,
                            phase = ProcessingPhase.SELECTION
                        )
                }
            }
        }
        // 3. report [OSCakeIssue]s
        if (OSCakeLoggerManager.hasLogger(SELECTOR_LOGGER)) handleOSCakeIssues(
            project,
            logger,
            config.selector?.issueLevel ?: -1
        )
        // 4. take care of issue level settings to create the correct output format
        takeCareOfIssueLevel()
        // 5. generate .zip and .oscc files
        createResultingFiles(archiveDir)
    }
}
