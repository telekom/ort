/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger

import org.ossreviewtoolkit.model.Identifier

/**
 * The [OSCakeLogger] manages a list of reported [OSCakeIssue]s and a [logger] for a specific [source]
  */
class OSCakeLogger(
    /**
     * [source] represents the origin (e.g. [CURATION_LOGGER] or [REPORTER_LOGGER]).
     */
    val source: String,
    /**
     * [logger] is the reference to the Apache Logger.
     */
    val logger: Logger
) {
    /**
     * List of reported [OSCakeIssue]s.
     */
    internal val osCakeIssues = mutableListOf<OSCakeIssue>()

    /**
     * Stores an issue in the map [osCakeIssues] and writes the [msg] with a specific prefix into the log file.
     */
    internal fun log(msg: String, level: Level, id: Identifier? = null, fileScope: String? = null) {
        osCakeIssues.add(OSCakeIssue(msg, level, id, fileScope))
        var prefix = ""
        if (id != null) prefix += id
        if (fileScope != null) {
            if (prefix == "") prefix += "FileScope: " + fileScope
            else prefix += " - FileScope: " + fileScope
        }
        if (prefix != "") prefix = "[$prefix]: "
        logger.log(level, source + ": " + prefix + msg)
    }
}
