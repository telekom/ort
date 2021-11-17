/*
 * Copyright (C) 2021 Deutsche Telekom AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
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
@JsonPropertyOrder("pid", "release", "repository", "id", "sourceRoot", "reuseCompliant", "hasIssues", "issues",
    "defaultLicensings", "dirLicensings", "reuseLicensings", "fileLicensings")
@JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = IssuesFilterCustom::class)
data class Pack(
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
    @get:JsonProperty("sourceRoot") val packageRoot: String = ""
) {
    /**
     * Package ID: e.g. "tdosca-tc06"  - part of the [id].
     */
    @JsonProperty("pid") val name = id.name
    /**
     * Namespace of the package: e.g. "de.tdosca.tc06" - part of the [id].
     */
    @JsonIgnore
    var namespace = id.namespace
    /**
     * version number of the package: e.g. "1.0" - part of the [id].
     */
    @JsonProperty("release") val version = id.version
    /**
     * [type] describes the package manager for the package: e.g. "Maven" - part of the [id].
     */
    @JsonIgnore
    var type = id.type
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
    var hasIssues: Boolean = false
    /**
     * contains issues for the package level
     */
    @JsonProperty("issues") val issueList: IssueList = IssueList()
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
