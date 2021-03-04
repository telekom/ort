/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * The FileLicense class wraps the information about the [license] and the name of the file containing the license
 * information [licenseTextInArchive]. An instance with null values may exist if the file was archived by the scanner
 * and not treated by other logic branches
 */
internal data class FileLicense(
    val license: String?,
    var licenseTextInArchive: String? = null,
    @JsonIgnore var startLine: Int = -1,
)
