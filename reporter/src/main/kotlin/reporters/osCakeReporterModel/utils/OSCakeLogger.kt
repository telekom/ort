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

package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils

import java.time.LocalDateTime

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.message.ObjectArrayMessage

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.config.OSCakeConfigParams

/**
 * The [OSCakeLogger] manages a list of reported [OSCakeIssue]s and a [logger] for a specific [source]
  */
class OSCakeLogger(
    /**
     * [source] represents the origin (e.g. "CURATION_LOGGER" or "REPORTER_LOGGER").
     */
    val source: String,
    /**
     * [logger] is the reference to the Apache Logger.
     */
    private val logger: Logger
) {
    private val csvLogger = LogManager.getLogger()
    /**
     * List of reported [OSCakeIssue]s.
     */
    internal val osCakeIssues = mutableListOf<OSCakeIssue>()

    /**
     * Stores an issue in the map [osCakeIssues] and writes the [msg] with a specific prefix into the log file.
     */
    fun log(
        msg: String,
        level: Level,
        id: Identifier? = null,
        fileScope: String? = null,
        reference: Any? = null,
        scope: ScopeLevel? = null,
        phase: ProcessingPhase? = null
    ) {
        var jsonPath = ""

        OSCakeIssue(
            if (source == REPORTER_LOGGER) msg else "<<$source>> $msg",
            level,
            id,
            fileScope,
            reference,
            scope,
            phase
        ).also {
            if (level != Level.DEBUG) osCakeIssues.add(it)
            // OSCake apps (Deduplicator, Curator, ...) have no params initialized
            if (OSCakeConfigParams.includeJsonPathInLogfile4ErrorsAndWarnings &&
                    (level == Level.ERROR || level == Level.WARN)
                ) jsonPath = it.generateJSONPath()
            val msgArr = ObjectArrayMessage(
                LocalDateTime.now().toString(), it.level, source, it.phase,
                it.id?.toCoordinates(), it.scope, it.fileScope, msg.replace("\n", "  -->"), jsonPath
            )
            when (level) {
                Level.DEBUG -> csvLogger.debug(msgArr)
                Level.INFO -> csvLogger.info(msgArr)
                Level.WARN -> csvLogger.warn(msgArr)
                Level.ERROR -> csvLogger.error(msgArr)
                else -> csvLogger.log(Level.INFO, msgArr.parameters.joinToString("|"))
            }
        }

        var prefix = ""
        if (id != null) prefix += id
        if (fileScope != null) {
            prefix += if (prefix == "") "File: $fileScope"
            else " - File: $fileScope"
        }
        if (prefix != "") prefix = "[$prefix]: "
        val jp = if (jsonPath != "") " - jsonPath: \"$jsonPath\"" else ""
        logger.log(level, "$source: <<$phase>> $prefix$msg$jp")
    }
}
