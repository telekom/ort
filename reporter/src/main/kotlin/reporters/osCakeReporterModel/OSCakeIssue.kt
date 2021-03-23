/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier

/**
 * [OSCakeIssue] represents a class containing information about problems which occurred during processing data of the
 * [OSCakeReporter] or the [CurationManager].
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
)
