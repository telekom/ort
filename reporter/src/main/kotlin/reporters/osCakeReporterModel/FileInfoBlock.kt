/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
 * The class FileInfoBlock holds information gathered from the native scan result files and represents it by
 * the collection [licenseTextEntries] and [copyrightTextEntries] for each analyzed file, defined in [path]
 */
internal data class FileInfoBlock(val path: String) {
    /**
     * [licenseTextEntries] represents a list of licenses and its properties bundled in the class [LicenseTextEntry]
     */
    val licenseTextEntries = mutableListOf<LicenseTextEntry> ()
    /**
     * [copyrightTextEntries] represents a list of copyrights ([CopyrightTextEntry])
     */
    val copyrightTextEntries = mutableListOf<CopyrightTextEntry>()
}
