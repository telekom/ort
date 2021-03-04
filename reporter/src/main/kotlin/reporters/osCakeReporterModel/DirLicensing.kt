/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * The class DirLicensing is a collection of [DirLicense] instances for the given path (stored in [scope])
 */

@JsonPropertyOrder("scope", "licenses")
internal data class DirLicensing(@get:JsonProperty("dirScope") val scope: String) {
    @JsonProperty("dirLicenses") val licenses = mutableListOf<DirLicense>()
}
