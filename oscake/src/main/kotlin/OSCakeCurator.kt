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
import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.FOUND_IN_FILE_SCOPE_DECLARED
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Project
import java.io.File
import java.io.IOException

class OSCakeCurator (private val config: OSCakeConfiguration, private val osccFile: File,
                     private val outputDir: File) {

    fun execute() {

        println(config.oscakeCurations?.directory + " ---- " + outputDir)
        val mapper = jacksonObjectMapper()
        var project: Project?
        try {
            val json = osccFile.readText()
            project = mapper.readValue<Project>(json)

            // reset values depending on oscc content
            project.packs.forEach { pack ->
                pack.defaultLicensings.forEach {
                    if (it.license != FOUND_IN_FILE_SCOPE_DECLARED)
                        it.declared = false
                }
            }
        } catch (e: IOException) {
            println(e.stackTraceToString())
        } finally {
            println("finally")
        }
    }
}
