/*
 * Copyright (C) 2021 Deutsche Telekom AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
