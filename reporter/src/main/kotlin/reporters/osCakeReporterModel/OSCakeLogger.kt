/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger

import org.ossreviewtoolkit.model.Identifier

class OSCakeLogger(val source: String, val logger: Logger) {

    internal val osCakeIssues = mutableListOf<OSCakeIssue>()

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
