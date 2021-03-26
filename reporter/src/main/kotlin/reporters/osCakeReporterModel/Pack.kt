/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

import java.util.SortedSet

import org.ossreviewtoolkit.model.Identifier

/**
 * The class [Pack] holds license information found in license files. The [Identifier] stores the unique name of the
 * project or package (e.g. "Maven:de.tdosca.tc06:tdosca-tc06:1.0"). License information is split up into different
 * [ScopeLevel]s: [defaultLicensings], [dirLicensings], etc.. If the sourcecode is not placed in the
 * root directory of the repository (e.g. git), than the property [packageRoot] shows the path to the root directory
  */
@JsonPropertyOrder("pid", "release", "repository", "id", "reuseCompliant", "hasIssues", "defaultLicensings",
    "dirLicensings", "reuseLicensings", "fileLicensings")
internal data class Pack(
    /**
     * Unique identifier for the package.
     */
    @JsonProperty("id") val id: Identifier,
    /**
     * [website] contains the URL directing to the source code repository.
     */
    @get:JsonProperty("repository") val website: String,
    /**
     * The [packageRoot] is set to the folder where the source code can be found (empty string = default).
     */
    @JsonIgnore val packageRoot: String
) {
    /**
     * Package ID: e.g. "tdosca-tc06"  - part of the [id].
     */
    @JsonProperty("pid") val name = id.name
    /**
     * Namespace of the package: e.g. "de.tdosca.tc06" - part of the [id].
     */
    @JsonIgnore
    val namespace = id.namespace
    /**
     * version number of the package: e.g. "1.0" - part of the [id].
     */
    @JsonProperty("release") val version = id.version
    /**
     * [type] describes the package manager for the package: e.g. "Maven" - part of the [id].
     */
    @JsonIgnore
    val type = id.type
    /**
     * [declaredLicenses] contains a set of licenses identified by the ORT analyzer.
     */
    @JsonIgnore
    var declaredLicenses: SortedSet<String> = sortedSetOf<String>()
    /**
     * If the package is REUSE compliant, this flag is set to true.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) var reuseCompliant: Boolean = false
    /**
     * [hasIssues] shows that issues have happened during processing.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) var hasIssues: Boolean = false
    /**
     *  [defaultLicensings] contains a list of [DefaultLicense]s  for non-REUSE compliant packages.
     */
    val defaultLicensings = mutableListOf<DefaultLicense>()
    /**
     *  This list is only filled for REUSE-compliant packages and contains a list of [DefaultLicense]s.
     */
    val reuseLicensings = mutableListOf<ReuseLicense>()
    /**
     *  [dirLicensings] contains a list of [DirLicensing]s for non-REUSE compliant packages.
     */
    val dirLicensings = mutableListOf<DirLicensing>()
    /**
     *  [fileLicensings] contains a list of [fileLicensings]s.
     */
    val fileLicensings = mutableListOf<FileLicensing>()
}
