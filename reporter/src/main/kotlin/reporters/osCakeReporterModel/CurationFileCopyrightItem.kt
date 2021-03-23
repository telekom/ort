/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
 * A class defining a curation for a copyright.
 */
internal data class CurationFileCopyrightItem(
    /**
     * The [modifier] defines the application of the curation: delete, insert or delete-all.
     */
    val modifier: String,
    /**
     * The optional [reason] is a description of the necessity of the curation.
     */
    val reason: String?,
    /**
     * The [copyright] is used to identify the specific copyright text; it may contain a string,
     * a placeholder "*" or null. The [copyright] may also contain wildcards, like: "*" or "?".
     */
    val copyright: String?
)
