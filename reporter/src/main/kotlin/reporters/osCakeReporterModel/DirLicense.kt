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
    /**
     * [license] contains the name of the license.
     */
    val license: String,
    /**
     * Represents the path to the file containing the license text in the archive.
     */
    var licenseTextInArchive: String? = null,
    /**
     * Shows the [path] to the file where the license was found.
     */
    @get:JsonProperty("foundInFileScope") val path: String? = null
)
