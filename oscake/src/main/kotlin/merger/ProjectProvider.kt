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

package org.ossreviewtoolkit.oscake.merger

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.oscake.MERGER_LOGGER
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Project
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.OSCakeLoggerManager

/**
 * [ProjectProvider] returns a [Project]-instance - deserialized from the provided source file
 */
internal class ProjectProvider private constructor() {
    companion object {
        private val mapper = jacksonObjectMapper()
        private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(MERGER_LOGGER) }

        fun getProject(source: File): Project? {
            var project: Project? = null
            try {
                val json = File(source.absolutePath).readText()
                project = mapper.readValue<Project>(json)
                // store the originating file name inside the project instance for collision detection
                project.packs.forEach {
                    it.origin = source.name
                }
            } catch (e: IOException) {
                logger.log(e.stackTraceToString(), Level.ERROR)
            }
            return project
        }
    }
}
