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
internal data class DirLicensing(
    /**
     * [scope] contains the name of the folder to which the licenses belong.
     */
    @get:JsonProperty("dirScope") val scope: String) {
    /**
     * [licenses] contains a list of [DirLicense]s.
     */
    @JsonProperty("dirLicenses") val licenses = mutableListOf<DirLicense>()
}
