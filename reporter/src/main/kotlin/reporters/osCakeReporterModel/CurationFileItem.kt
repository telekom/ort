/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonProperty

class CurationFileItem(
    @JsonProperty("file_scope") val fileScope: String,
    @JsonProperty("file_licenses") val fileLicenses: List<CurationFileLicenseItem>?,
    @JsonProperty("file_copyrights") val fileCopyrights: List<CurationFileCopyrightItem>?
)
