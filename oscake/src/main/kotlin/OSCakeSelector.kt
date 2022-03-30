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

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.model.config.OSCakeSelector
import org.ossreviewtoolkit.oscake.selector.SelectorManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*

/**
 * The [OSCakeSelector] provides a mechanism to select a specific license from dual licenses in an *.oscc file.
 */
class OSCakeSelector(private val config: OSCakeConfiguration, private val commandLineParams: Map<String, String>) {
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(SELECTOR_LOGGER) }

    /**
    * Checks valid commandline parameters and starts the resolving algorithm
    */
    fun execute() {
        require(config.selector != null) { "No \"selector\" section found in ort.conf" }
        val osccFile = File(commandLineParams["osccFile"]!!)
        val outputDir = File(commandLineParams["outputDir"]!!)
        val project = Project.osccToModel(osccFile, logger, ProcessingPhase.SELECTION)

        project.isProcessingAllowed(logger, osccFile, listOf(DEDUPLICATION_AUTHOR, CURATION_AUTHOR, MERGER_AUTHOR,
            SELECTOR_AUTHOR))

        project.config?.let { configInfo ->
            addParamsToConfig(config, commandLineParams, this)?.let {
                configInfo.selector = it
            }
        }

        SelectorManager(project, outputDir, osccFile.absolutePath, config, commandLineParams).manage()
    }
}
