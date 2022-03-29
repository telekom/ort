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

package org.ossreviewtoolkit.oscake.injector

import java.io.File

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.INJECTOR_LOGGER
import org.ossreviewtoolkit.oscake.common.ActionInfo
import org.ossreviewtoolkit.oscake.common.ActionManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeConfigParams
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Project
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.handleOSCakeIssues

/**
 * The [InjectorManager] handles the entire injector process: reads and analyzes the distributor and packageType
 * files, applies the different actions and writes the results into the output files - one oscc compliant
 * file in json format and a zip-archive containing the license texts.
 */
internal class InjectorManager(
    /**
    * The [project] contains the processed output from the OSCakeReporter - specifically a list of packages.
    */
    override val project: Project,
    /**
    * The generated files are stored in the folder [outputDir]
    */
    override val outputDir: File,
    /**
    * The name of the reporter's output file which is extended by the [InjectorManager]
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
) : ActionManager(project, outputDir, reportFilename, config,
    ActionInfo.injector(config.injector?.issueLevel ?: -1), commandLineParams) {

    /**
     * The [distributorProvider] contains a list of [DistributorPackage]s to be applied.
     */
    private var distributorProvider = DistributorProvider(File(config.injector?.distribution?.directory!!))
    /**
     * The [packageTypeProvider] contains a list of [PackageTypePackage]s to be applied.
     */
    private var packageTypeProvider = PackageTypeProvider(File(config.injector?.packageType?.directory!!))

    /**
     * The method takes the reporter's output, checks and updates the reported "hasIssues", applies the
     * injector actions, reports emerged issues and finally, writes the output files.
     */
    internal fun manage() {
        // 1. Process distributor-package if it's valid and applicable
        if (config.injector?.distribution?.enabled == true)
            project.packs.forEach {
                distributorProvider.getActionFor(it.id, true)?.
                process(it, OSCakeConfigParams.setParamsForCompatibilityReasons(project), archiveDir, logger)
            }
        // 2. Process packageType-package if it's valid and applicable
        if (config.injector?.packageType?.enabled == true)
            project.packs.forEach {
                packageTypeProvider.getActionFor(it.id, true)?.
                process(it, OSCakeConfigParams.setParamsForCompatibilityReasons(project), archiveDir, logger)
            }
        // 3. report [OSCakeIssue]s
        if (OSCakeLoggerManager.hasLogger(INJECTOR_LOGGER)) handleOSCakeIssues(project, logger,
            config.injector?.issueLevel ?: -1)
        // 4. take care of issue level settings to create the correct output format
        takeCareOfIssueLevel()
        // 5. generate .zip and .oscc files
        createResultingFiles(archiveDir)
    }
}
