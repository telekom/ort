/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
 * The class FileInfoBlock holds information gathered from the native scan result files and represents it by
 * the collection [licenseTextEntries] and [copyrightTextEntries] for each analyzed file, defined in [path]
 */
internal data class FileInfoBlock(val path: String) {
    val licenseTextEntries = mutableListOf<LicenseTextEntry> ()
    val copyrightTextEntries = mutableListOf<CopyrightTextEntry>()
}
