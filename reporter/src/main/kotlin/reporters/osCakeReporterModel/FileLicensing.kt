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
internal data class FileLicensing(
    /**
     * [scope] contains the name of the file to which the licenses belong.
     */
    @get:JsonProperty("fileScope") val scope: String) {
    /**
     * Represents the path to the file containing the license text in the archive.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL) var fileContentInArchive: String? = null
    /**
     * [licenses] keeps a list of all license findings for this file.
     */
    @JsonProperty("fileLicenses") val licenses = mutableListOf<FileLicense>()
    /**
     * [copyrights] keeps a list of all copyright statements for this file.
     */
    @JsonProperty("fileCopyrights") val copyrights = mutableListOf<FileCopyright>()
}
