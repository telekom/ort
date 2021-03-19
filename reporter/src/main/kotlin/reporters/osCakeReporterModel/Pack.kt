/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import org.json.JSONPropertyIgnore
import java.util.SortedSet
import org.ossreviewtoolkit.model.Identifier

/**
 * The class [Pack] holds license information found in license files. The [Identifier] stores the unique name of the
 * project or package (e.g. "Maven:de.tdosca.tc06:tdosca-tc06:1.0"). License information is split up into different
 * [ScopeLevel]s: [defaultLicensings], [dirLicensings], and [fileLicensings]. If the sourcecode is not placed in the
 * root directory of the repository (e.g. git), than the property [packageRoot] shows the path to the root directory
  */
@JsonPropertyOrder("pid", "release", "repository", "id", "reuseCompliant", "hasIssues", "defaultLicensings",
    "dirLicensings", "reuseLicensings", "fileLicensings")
internal data class Pack(
    @JsonProperty("id") val id: Identifier,
    @JsonIgnore val website: String,
    @JsonIgnore val packageRoot: String
) {
    @JsonProperty("pid") val name = id.name
    @JsonIgnore
    val namespace = id.namespace
    @JsonProperty("release") val version = id.version
    @JsonIgnore
    val type = id.type
    @JsonIgnore
    var declaredLicenses: SortedSet<String> = sortedSetOf<String>()
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) var reuseCompliant: Boolean = false
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) var hasIssues: Boolean = false
    val repository = website
    val defaultLicensings = mutableListOf<DefaultLicense>()
    val reuseLicensings = mutableListOf<ReuseLicense>()
    val dirLicensings = mutableListOf<DirLicensing>()
    val fileLicensings = mutableListOf<FileLicensing>()
}
