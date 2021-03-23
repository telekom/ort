/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A class defining curations for licenses and copyrights for a specific file defined in [fileScope].
 */
internal data class CurationFileItem(
    /**
     * Relative path to the specified file - may also contain the "packageRoot" as defined in the class pack.
     */
    @JsonProperty("file_scope") val fileScope: String,
    /**
     * List of [fileLicenses] to be processed.
     */
    @JsonProperty("file_licenses") val fileLicenses: List<CurationFileLicenseItem>?,
    /**
     * List of [fileCopyrights] to be processed.
     */
    @JsonProperty("file_copyrights") val fileCopyrights: List<CurationFileCopyrightItem>?
)
