/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
 * The class [CopyrightTextEntry] contains information about the location and the corresponding text of the
 * copyright statement.
 */
internal data class CopyrightTextEntry(
    /**
     * [startLine] of the textblock which contains the copyright text in the source file.
     */
    override var startLine: Int = -1,
    /**
     * [endLine] of the textblock which contains the copyright text in the source file.
     */
    override var endLine: Int = -1,
    /**
     * [matchedText] contains the copyright text provided by the scanner output.
     */
    var matchedText: String? = null
) : TextEntry
