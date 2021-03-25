/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The class [Project] wraps the meta information ([complianceArtifactCollection]) of the OSCakeReporter as well
 * as a list of included projects and packages store in instances of [Pack]
 */
internal data class Project(@JsonIgnore val cid: String) {
    /**
     * [hasIssues] shows if problems occurred during processing the data.
     */
    var hasIssues: Boolean = false
    /**
     * [complianceArtifactCollection] contains meta data about the project.
     */
    val complianceArtifactCollection = ComplianceArtifactCollection(cid)
    /**
     * [packs] is a list of packages [pack] which are part of the project.
     */
    @JsonProperty("complianceArtifactPackages") val packs: MutableList<Pack> = mutableListOf<Pack>()
}
