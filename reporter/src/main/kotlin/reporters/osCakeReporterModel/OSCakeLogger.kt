/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.createAndLogIssue

class OSCakeLogger(val source: String) {
    val osCakeIssues = mutableListOf<OSCakeIssue>()
    var somethingForOscc = false // for future use

    fun log(msg: String, severity: Severity, intoOscc: Boolean = false) {
        osCakeIssues.add(OSCakeIssue(msg, severity, intoOscc))
        if (intoOscc) somethingForOscc = true

        createAndLogIssue(
            source = "OSCakeReporter",
            message = "$severity: $msg",
            // workaround, because in ORT: HINT are not shown even when log4j is set to INFO
            severity = if (severity == Severity.HINT) Severity.WARNING else severity
        )
    }
}
