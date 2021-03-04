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
    override var startLine: Int = -1,
    override var endLine: Int = -1,
    var license: String? = null,
    var isLicenseText: Boolean = false,
    var isInstancedLicense: Boolean = false,
    var isLicenseNotice: Boolean = false
) : TextEntry
