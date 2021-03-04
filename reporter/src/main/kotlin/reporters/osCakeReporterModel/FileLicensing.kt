/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * The class FileLicensing is a collection of [FileLicense] instances for the given path (stored in [scope])
 */
@JsonPropertyOrder("scope", "fileContentInArchive", "licenses")
internal data class FileLicensing(@get:JsonProperty("fileScope") val scope: String) {
    @JsonInclude(JsonInclude.Include.NON_NULL) var fileContentInArchive: String? = null
    @JsonProperty("fileLicenses") val licenses = mutableListOf<FileLicense>()
    @JsonProperty("fileCopyrights") val copyrights = mutableListOf<FileCopyright>()
}
