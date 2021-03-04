/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.ossreviewtoolkit.model.Severity

data class OSCakeIssue(val msg: String, val severity: Severity, val intoOscc: Boolean)
