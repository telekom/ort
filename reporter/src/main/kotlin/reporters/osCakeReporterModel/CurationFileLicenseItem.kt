/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonProperty

class CurationFileLicenseItem(
    val modifier: String,
    val reason: String?,
    val license: String,
    @JsonProperty("license_text_in_archive") val licenseTextInArchive: String?
)
