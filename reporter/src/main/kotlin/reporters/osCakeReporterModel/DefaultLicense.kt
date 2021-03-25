/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * The class DefaultLicense represents [license]-information and where to find the corresponding file
 * - stored in [licenseTextInArchive]. [declared] is set to false as soon as a valid license info is found in a file
 * otherwise it is left to true, as an indicator that the info was provided by the analyzer module (e.g. pom.xml)
 */
@JsonPropertyOrder("foundInFileScope", "license", "licenseTextInArchive")
internal data class DefaultLicense(
    /**
     * [license] contains the name of the license.
     */
    val license: String?,
    /**
     * Shows the [path] to the file where the license was found.
     */
    @get:JsonProperty("foundInFileScope") val path: String?,
    /**
     * Represents the path to the file containing the license text in the archive.
     */
    var licenseTextInArchive: String? = null,
    /**
     * Categorizes the [license] as declared, if the license was set by the analyzer and not found
     * by the scanner.
     */
    @JsonIgnore var declared: Boolean = true
)
