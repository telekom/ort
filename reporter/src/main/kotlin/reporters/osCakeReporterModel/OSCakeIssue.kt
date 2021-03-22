/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier

internal data class OSCakeIssue(
    val msg: String,
    val level: Level,
    val id: Identifier?,
    val fileScope: String?,
)
