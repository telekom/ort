/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * The class DefaultLicense represents [license]-information and where to find the corresponding file
 * - stored in [licenseTextInArchive]. [declared] is set to false as soon a valid license info is found in a file
 * otherwise it is left to true, as an indicator that the info was provided by the analyzer module (e.g. pom.xml)
 */
@JsonPropertyOrder("foundInFileScope", "license", "licenseTextInArchive")
internal data class DefaultLicense(val license: String?,
                                   @get:JsonProperty("foundInFileScope") val path: String?,
                                   var licenseTextInArchive: String? = null,
                                   @JsonIgnore var declared: Boolean = true)
