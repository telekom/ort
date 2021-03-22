/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("foundInFileScope", "license", "licenseTextInArchive")
class ReuseLicense(val license: String?,
                    @get:JsonProperty("foundInFileScope") val path: String?,
                    var licenseTextInArchive: String? = null)
