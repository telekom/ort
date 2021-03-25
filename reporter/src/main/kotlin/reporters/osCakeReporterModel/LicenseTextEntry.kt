/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
 * The class LicenseTextEntry represents a license information, found in the scanner files. Either [isLicenseNotice]
 * or [isLicenseText] must be set to true. The property [isInstancedLicense] depends on the category of the license,
 * which is found in the file "license-classifications.yml"
 */
internal data class LicenseTextEntry(
    /**
     * [startLine] contains the starting line of the license text in the source file.
     */
    override var startLine: Int = -1,
    /**
     * [startLine] contains the last line of the license text in the source file.
     */
    override var endLine: Int = -1,
    /**
     * [license] is the name of the license.
     */
    var license: String? = null,
    /**
     * If the scanner categorized the license finding as license text, [isLicenseText] is set to true.
     */
    var isLicenseText: Boolean = false,
    var isInstancedLicense: Boolean = false,
    /**
     * If the scanner identified a license as reference, notice, etc. and not as text [isLicenseNotice] is set to true.
     */
    var isLicenseNotice: Boolean = false
) : TextEntry
