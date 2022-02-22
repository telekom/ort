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

import java.io.File

import kotlin.system.exitProcess

import org.apache.logging.log4j.Level

/**
 * The class [OSCakeRoot] represents the root node for the output; currently, it only consists of the property
 * [Project]
 */
class OSCakeRoot {
    /**
     * The [project] contains the project's packages and resolved license information.
     */
    var project: Project = Project()

    fun isProcessingAllowed(logger: OSCakeLogger, osccFile: File, authorList: List<String>): Boolean {
        if (project.containsHiddenSections == true) {
            logger.log("The file \"${osccFile.name}\" cannot be processed, because some sections are missing!" +
                    " (maybe it was created with config option \"hideSections\")", Level.ERROR,
                phase = ProcessingPhase.CURATION)
            exitProcess(12)
        }
        if (authorList.contains(project.complianceArtifactCollection.author)) {
            logger.log("The file \"${osccFile.name}\" cannot be processed, because it was already processed" +
                    " in a former run! Check \"author\" in input file!", Level.ERROR, phase = ProcessingPhase.CURATION)
            exitProcess(10)
        }
        // A merged oscc-file cannot be curated because there is no config information left (scopePatterns
        // are missing); additionally the tag "mergedIDs" contains a list of merged ComplianceArtifactCollection
        if (project.complianceArtifactCollection.mergedIds.isNotEmpty()) {
            logger.log(
                "The given project is a merged project and cannot be curated anymore!",
                Level.ERROR, phase = ProcessingPhase.CURATION
            )
            exitProcess(11)
        }
        return true
    }
}
