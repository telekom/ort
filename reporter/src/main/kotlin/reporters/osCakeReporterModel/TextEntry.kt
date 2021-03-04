/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
 * The class TextEntry wraps the [startLine] and [endLine] of a license or copyright finding
 */
internal interface TextEntry {
    var startLine: Int
    var endLine: Int
}
