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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.curator.CurationManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*

class OSCakeCurator(private val config: OSCakeConfiguration, private val osccFile: File,
                     private val outputDir: File, private val ignoreRootWarnings: Boolean) {

    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(CURATION_LOGGER) }

    /**
     * Generates the json from file and starts the curation process
     */
    fun execute() {
        val mapper = jacksonObjectMapper()
        var project: Project? = null
        try {
            val json = osccFile.readText()
            project = mapper.readValue<Project>(json)
        } catch (e: IOException) {
            logger.log("Invalid json format found in file: \"$osccFile\".\n ${e.message} ",
                Level.ERROR, phase = ProcessingPhase.CURATION)
        } finally {
            project?.let {
                // rebuild info for model completeness built on information in oscc
                project.packs.forEach { pack ->
                    pack.namespace = pack.id.namespace
                    pack.type = pack.id.type
                    pack.defaultLicensings.forEach {
                        it.declared = it.license == FOUND_IN_FILE_SCOPE_DECLARED
                    }
                }
                val osc = OSCakeRoot()
                osc.project = project
                CurationManager(osc.project, outputDir, osccFile.absolutePath, config, ignoreRootWarnings).manage()
            }
        }
    }
}
