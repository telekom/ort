/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * A class representing a license item for REUSE packages [fileScope].
 */
@JsonPropertyOrder("foundInFileScope", "license", "licenseTextInArchive")
internal class ReuseLicense(
    /**
     * [license] keeps the name of the license.
     */
    val license: String?,
    /**
     * Path to the corresponding file (source).
     */
    @get:JsonProperty("foundInFileScope") val path: String?,
    /**
     * Path to the license file in the archive (target)
     */
    var licenseTextInArchive: String? = null
)
