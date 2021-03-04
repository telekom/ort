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
    val complianceArtifactCollection = ComplianceArtifactCollection(cid)
    @JsonProperty("complianceArtifactPackages") val packs: MutableList<Pack> = mutableListOf<Pack>()
}
