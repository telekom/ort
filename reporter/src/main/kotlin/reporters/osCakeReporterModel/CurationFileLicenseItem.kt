/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A class defining a curation for a license.
 */
internal data class CurationFileLicenseItem(
    /**
     * The [modifier] defines the application of the curation: delete, insert or update.
     */
    val modifier: String,
    /**
     * The optional [reason] is a description of the necessity of the curation.
     */
    val reason: String?,
    /**
     * The [license] is used to identify the specific license text; it may contain a valid license string,
     * a placeholder "*" or null (null is treated like a specific license in order to curate license text entries with
     * null.
     */
    val license: String?,
    /**
     * [licenseTextInArchive] represents a relative path to the file containing the license information in the archive;
     * it also may consist of a placeholder "*" for any file or null for no file at all.
     */
    @JsonProperty("license_text_in_archive") val licenseTextInArchive: String?
)
