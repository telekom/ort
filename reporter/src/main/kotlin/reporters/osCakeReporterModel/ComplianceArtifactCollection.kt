/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonPropertyOrder

import java.time.LocalDateTime

/**
 * The class ComplianceArtifactCollection contains meta information about the run of the OSCakeReporter. Currently,
 * only zip archives are supported
 */
@JsonPropertyOrder("cid", "author", "release", "date", "archivePath", "archiveType")
internal data class ComplianceArtifactCollection(var cid: String) {
    val author = "OSCake-Reporter"
    val release = Release.NUMBER
    val date = LocalDateTime.now().toString()
    var archivePath = "./licensefiles.zip"
    var archiveType = "ZIP"
}
