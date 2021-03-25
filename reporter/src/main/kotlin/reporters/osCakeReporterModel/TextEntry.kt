/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
 * The class TextEntry wraps the [startLine] and [endLine] of a license or copyright finding.
 */
internal interface TextEntry {
    /**
     * [startLine] contains the line number where the license text begins.
     */
    var startLine: Int
    /**
     * [endLine] contains the line number where the license text ends.
     */
    var endLine: Int
}
