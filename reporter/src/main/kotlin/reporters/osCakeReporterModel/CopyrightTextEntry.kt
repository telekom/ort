/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
 * The class CopyrightTextEntry contains information about the location and the corresponding text of the
 * copyright statement
 */
internal data class CopyrightTextEntry(
    override var startLine: Int = -1,
    override var endLine: Int = -1,
    var matchedText: String? = null
) : TextEntry
