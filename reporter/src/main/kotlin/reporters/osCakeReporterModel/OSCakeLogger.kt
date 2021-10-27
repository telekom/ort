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
import org.apache.logging.log4j.Logger

import org.ossreviewtoolkit.model.Identifier

/**
 * The [OSCakeLogger] manages a list of reported [OSCakeIssue]s and a [logger] for a specific [source]
  */
internal class OSCakeLogger(
    /**
     * [source] represents the origin (e.g. [CURATION_LOGGER] or [REPORTER_LOGGER]).
     */
    val source: String,
    /**
     * [logger] is the reference to the Apache Logger.
     */
    private val logger: Logger
) {
    /**
     * List of reported [OSCakeIssue]s.
     */
    internal val osCakeIssues = mutableListOf<OSCakeIssue>()

    /**
     * Stores an issue in the map [osCakeIssues] and writes the [msg] with a specific prefix into the log file.
     */
    internal fun log(
        msg: String,
        level: Level,
        id: Identifier? = null,
        fileScope: String? = null,
        reference: Any? = null,
        scope: ScopeLevel? = null,
        phase: ProcessingPhase? = null
    ) {
        osCakeIssues.add(OSCakeIssue(msg, level, id, fileScope, reference, scope, phase))

        // check log levels from config
        when (reference) {
            is DefaultLicense -> {
                reference.hasIssues = true
                if (reference.issues == null) reference.issues = Issues()
                if (level == Level.INFO) {
                    if (reference.issues!!.infos == null) reference.issues!!.infos = mutableListOf<String>()
                    reference.issues!!.infos!!.add(msg)
                }
                if (level == Level.WARN) {
                    if (reference.issues!!.warnings == null) reference.issues!!.warnings = mutableListOf<String>()
                    reference.issues!!.warnings!!.add(msg)
                }
                if (level == Level.ERROR) {
                    if (reference.issues!!.errors == null) reference.issues!!.errors = mutableListOf<String>()
                    reference.issues!!.errors!!.add(msg)
                }
            }
            is DirLicense -> {
                reference.hasIssues = true
                if (reference.issues == null) reference.issues = Issues()
                if (level == Level.INFO) {
                    if (reference.issues!!.infos == null) reference.issues!!.infos = mutableListOf<String>()
                    reference.issues!!.infos!!.add(msg)
                }
                if (level == Level.WARN) {
                    if (reference.issues!!.warnings == null) reference.issues!!.warnings = mutableListOf<String>()
                    reference.issues!!.warnings!!.add(msg)
                }
                if (level == Level.ERROR) {
                    if (reference.issues!!.errors == null) reference.issues!!.errors = mutableListOf<String>()
                    reference.issues!!.errors!!.add(msg)
                }
            }
        }

        var prefix = ""
        if (id != null) prefix += id
        if (fileScope != null) {
            prefix += if (prefix == "") "FileScope: $fileScope"
            else " - FileScope: $fileScope"
        }
        if (prefix != "") prefix = "[$prefix]: "
        logger.log(level, "$source: $prefix$msg")
    }
}
