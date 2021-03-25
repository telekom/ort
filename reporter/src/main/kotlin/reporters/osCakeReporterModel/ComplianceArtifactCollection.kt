/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonPropertyOrder

import java.time.LocalDateTime

/**
 * The class [ComplianceArtifactCollection] contains meta information about the run of the OSCakeReporter. Currently,
 * only zip archives are supported.
 */
@JsonPropertyOrder("cid", "author", "release", "date", "archivePath", "archiveType")
internal data class ComplianceArtifactCollection(
    /**
     * [cid] is the Identifier of this project e.g.: "Maven:de.tdosca.tc06:tdosca-tc06:1.0".
     */
    var cid: String
) {
    val author = "OSCake-Reporter"
    /**
     * Represents the release number of the OSCakeReporter which was used when creating the file.
     */
    val release = Release.NUMBER
    /**
     * [date] keeps the creation timestamp of the report.
     */
    val date = LocalDateTime.now().toString()
    /**
     * [archivePath] describes the path to the archive file containing the processed license files.
     */
    var archivePath = "./licensefiles.zip"
    /**
     * In current versions only zip files are used.
     */
    var archiveType = "ZIP"
}
