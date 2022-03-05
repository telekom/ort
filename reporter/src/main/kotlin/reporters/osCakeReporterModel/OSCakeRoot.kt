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

package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

import kotlin.system.exitProcess

import org.apache.logging.log4j.Level
import java.io.IOException

/**
 * The class [OSCakeRoot] represents the root node for the output; currently, it only consists of the property
 * [Project]
 */
class OSCakeRoot() {
    /**
     * The [project] contains the project's packages and resolved license information.
     */
    var project: Project = Project()

    fun isProcessingAllowed(logger: OSCakeLogger, osccFile: File, authorList: List<String>): Boolean {
        if (authorList.contains(project.complianceArtifactCollection.author)) {
            logger.log("The file \"${osccFile.name}\" cannot be processed, because it was already processed" +
                    " in a former run! Check \"author\" in input file!", Level.ERROR, phase = ProcessingPhase.CURATION)
            exitProcess(10)
        }
        if (project.containsHiddenSections == true) {
            logger.log("The file \"${osccFile.name}\" cannot be processed, because some sections are missing!" +
                    " (maybe it was created with config option \"hideSections\")", Level.ERROR,
                phase = ProcessingPhase.CURATION)
            exitProcess(12)
        }
        return true
    }
}
