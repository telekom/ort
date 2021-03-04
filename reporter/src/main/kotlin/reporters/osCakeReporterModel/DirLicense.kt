/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * The DirLicense class wraps the information about the [license] the name of the file containing the license
 * information [licenseTextInArchive] and the corresponding [path]
 */
@JsonPropertyOrder("foundInFileScope", "license", "licenseTextInArchive")
internal data class DirLicense(
    val license: String,
    var licenseTextInArchive: String? = null,
    @get:JsonProperty("foundInFileScope") val path: String? = null
)
