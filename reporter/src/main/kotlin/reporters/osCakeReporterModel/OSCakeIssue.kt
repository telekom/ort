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

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier

/**
 * [OSCakeIssue] represents a class containing information about problems which occurred during processing data of the
 * OSCakeReporter or the CurationManager.
  */
internal data class OSCakeIssue(
    /**
     * [msg] contains the description of the problem.
     */
    val msg: String,
    /**
     * [level] shows the severity of the problem (classification as defined in Apache log4j2).
     */
    val level: Level,
    /**
     * If the problem can be assigned to a specific package, its [id] is stored.
     */
    val id: Identifier?,
    /**
     * [fileScope] is set, if the problem happens in conjunction with a specific file path.
     */
    val fileScope: String?,
    /**
     * name of the license which is affected
     */
    val reference: Any?,
    /**
     * describes on which level the error occurred
     */
    val scope: ScopeLevel?,
    /**
     * describes in which phase the error occurred
     */
    val phase: ProcessingPhase?
) {
    internal fun generateJSONPath(): String {
        // Root-Level
        if (id == null) {
            return "\$.hasIssues"
        }
        // Package-Level
        if (scope != ScopeLevel.DIR && scope != ScopeLevel.DEFAULT) {
            return "\$.complianceArtifactPackages[?(@.pid=='${id.name}')]"
        }
        if (scope == ScopeLevel.DEFAULT) {
            val license = (reference as DefaultLicense).license
            return "\$.complianceArtifactPackages[?(@.pid=='${id.name}')].defaultLicensings" +
                    "[?(@.license=='$license')]"
        }
        if (scope == ScopeLevel.DIR) {
            val license = (reference as DirLicense).license
            val fileName = fileScope ?: ""
            val p = fileName.indexOfLast { it == '/' }
            val dirScope = if (p > -1) fileName.substring(0, p) else ""
            return "\$.complianceArtifactPackages[?(@.pid=='${id.name}')].dirLicensings[?(@.dirScope=='$dirScope')]." +
                    "dirLicenses[?(@.foundInFileScope=='$fileName' && @.license=='$license')]"
        }
        return ""
    }
}
