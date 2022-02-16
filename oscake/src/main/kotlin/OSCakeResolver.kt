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

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.curator.CurationManager
import org.ossreviewtoolkit.oscake.resolver.ResolverManager
import org.ossreviewtoolkit.oscake.resolver.ResolverProvider
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.osccToModel
import java.io.File

class OSCakeResolver(private val config: OSCakeConfiguration, private val commandLineParams: Map<String, String>) {
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(RESOLVER_LOGGER) }

    fun execute() {
        val osccFile = File(commandLineParams["osccFile"]!!)
        val outputDir = File(commandLineParams["outputDir"]!!)

        val osc = osccToModel(osccFile, logger, ProcessingPhase.CURATION)

        require(osc.isProcessingAllowed(logger, osccFile, listOf(DEDUPLICATION_AUTHOR, RESOLVER_AUTHOR)))

        addParamsToConfig(config, osc, commandLineParams, this)

        println(outputDir)
        println(osc.project.complianceArtifactCollection.author)

        //val r = ResolverProvider(File(config.resolver?.directory!!))
        //println(r.actions)

        ResolverManager(osc.project, outputDir, osccFile.absolutePath, config, commandLineParams).manage()

    }
}
