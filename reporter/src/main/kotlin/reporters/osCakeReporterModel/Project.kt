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

/**
 * The class [Project] wraps the meta information ([complianceArtifactCollection]) of the OSCakeReporter as well
 * as a list of included projects and packages store in instances of [Pack]
 */
@JsonPropertyOrder("hasIssues", "issues", "config", "complianceArtifactCollection", "complianceArtifactPackages")
@JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = IssuesFilterCustom::class)
internal data class Project(@JsonIgnore val cid: String) {
    /**
     * [hasIssues] shows if problems occurred during processing the data.
     */
    var hasIssues: Boolean = false
    /**
     * contains issues for the project level
     */
    @JsonProperty("issues") val issueList: IssueList = IssueList()
    /**
     * contains the runtime configuration - commandline parameters and oscake.conf
     */
    var config: OSCakeConfigInfo? = null
    /**
     * [complianceArtifactCollection] contains metadata about the project.
     */
    val complianceArtifactCollection = ComplianceArtifactCollection(cid)
    /**
     * [packs] is a list of packages [Pack] which are part of the project.
     */
    @JsonProperty("complianceArtifactPackages") val packs: MutableList<Pack> = mutableListOf<Pack>()
}
