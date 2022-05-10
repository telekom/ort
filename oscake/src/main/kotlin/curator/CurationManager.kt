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

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.CURATION_LOGGER
import org.ossreviewtoolkit.oscake.common.ActionInfo
import org.ossreviewtoolkit.oscake.common.ActionManager
import org.ossreviewtoolkit.oscake.packageModifierMap
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeConfigParams
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Pack
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Project
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.deleteFromArchive
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.getLicensesFolderPrefix
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.handleOSCakeIssues

/**
 * The [CurationManager] handles the entire curation process: reads and analyzes the curation files,
 * prepares the list of packages (passed by the reporter), applies the curations and writes the results into the output
 * files - one oscc compliant file in json format and a zip-archive containing the license texts.
 */
internal class CurationManager(
    /**
     * The [project] contains the processed output from the OSCakeReporter - specifically a list of packages.
     */
    override val project: Project,
    /**
     * The generated files are stored in the folder [outputDir]
     */
    override val outputDir: File,
    /**
     * The name of the reporter's output file which is extended by the [CurationManager]
     */
    override val reportFilename: String,
    /**
     * Configuration in ort.conf
     */
    override val config: OSCakeConfiguration,
    /**
     * Map of used commandline parameters
     */
    override val commandLineParams: Map<String, String>,
    ) : ActionManager(
        project,
        outputDir,
        reportFilename,
        config,
        ActionInfo.curator(config.curator?.issueLevel ?: -1), commandLineParams
) {
    /**
     * [ignoreRootWarnings] is set via the commandline parameters and determines if the project is processed despite
     * warnings
     */
    private val ignoreRootWarnings: Boolean = commandLineParams.getOrDefault("ignoreRootWarnings", "false").toBoolean()
    /**
     * The [curationProvider] contains a list of [CurationPackage]s to be applied.
     */
    private var curationProvider = CurationProvider(
        File(config.curator?.directory!!),
        File(config.curator?.fileStore!!)
    )

    /**
     * The method takes the reporter's output, checks and updates the reported "hasIssues", prioritizes the
     * package modifiers, applies the curations, reports emerged issues and finally, writes the output files.
     */
    internal fun manage() {
        // 0. Reset hasIssues = false - will be newly set at the end of the process
        project.resetIssues()
        // 1. handle packageModifiers
        val orderByModifier = packageModifierMap.keys.withIndex().associate { it.value to it.index }
        curationProvider.actions.sortedBy { orderByModifier[(it as CurationPackage).packageModifier] }
            .forEach { packageCuration ->

            packageCuration as CurationPackage
            when (packageCuration.packageModifier) {
                "insert" -> if (project.packs.none { it.id == packageCuration.id }) {
                    eliminateIssueFromRoot(project.issueList, packageCuration.id.toCoordinates())
                    Pack(packageCuration.id, packageCuration.repository ?: "", packageCuration.packageRoot ?: "")
                        .apply {
                            project.packs.add(this)
                            reuseCompliant = checkReuseCompliance(this, packageCuration)
                        }
                    } else {
                        logger.log(
                            "Package: \"${packageCuration.id}\" already exists - no duplication!",
                            Level.INFO,
                            phase = ProcessingPhase.CURATION
                        )
                    }
                "delete" -> {
                    eliminateIssueFromRoot(project.issueList, packageCuration.id.toCoordinates())
                    deletePackage(packageCuration, archiveDir)
                }
            }
        }

        // 2. curate each package regarding the "modifier" - insert, delete, update
        OSCakeConfigParams.setParamsFromProject(project)
        project.packs.forEach { pack ->
            curationProvider.getActionFor(pack.id)?.takeIf { (it as CurationPackage).packageModifier != "delete" }
                ?.process(pack, archiveDir, logger, File(config.curator?.fileStore!!))
        }

        // 3. report [OSCakeIssue]s
        if (OSCakeLoggerManager.hasLogger(CURATION_LOGGER)) handleOSCakeIssues(
            project,
            logger,
            config.curator?.issueLevel ?: -1
        )

        // 4. eliminate root level warnings (only warnings from reporter) when option is set
        if (ignoreRootWarnings) eliminateRootWarnings()

        // 5. take care of issue level settings to create the correct output format
        takeCareOfIssueLevel()

        // 6. generate .zip and .oscc files
        createResultingFiles(archiveDir)
    }

    /**
     * Checks if the package is reuse compliant
     */
    private fun checkReuseCompliance(pack: Pack, curationPackage: CurationPackage): Boolean =
        curationPackage.curations?.any {
            it.fileScope.startsWith(getLicensesFolderPrefix(pack.packageRoot))
        } ?: false

    /**
     * Deletes a package from project
     */
    private fun deletePackage(curationPackage: CurationPackage, archiveDir: File) {
        val packsToDelete = mutableListOf<Pack>()
        project.packs.filter { curationProvider.getActionFor(it.id) == curationPackage }.forEach { pack ->
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
