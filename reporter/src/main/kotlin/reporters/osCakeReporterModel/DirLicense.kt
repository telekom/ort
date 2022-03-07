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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * The DirLicense class wraps the information about the [license] the name of the file containing the license
 * information [licenseTextInArchive] and the corresponding [path]
 */
@JsonPropertyOrder("foundInFileScope", "license", "originalLicenses", "licenseTextInArchive", "hasIssues", "issues")
// work around with custom filter, because a declaration on property level "issues" did not work
@JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = IssuesFilterCustom::class)
data class DirLicense(
    /**
     * [license] contains the name of the license.
     */
    val license: String,
    /**
     * Represents the path to the file containing the license text in the archive.
     */
    var licenseTextInArchive: String? = null,
    /**
     * Shows the [path] to the file where the license was found.
     */
    @get:JsonProperty("foundInFileScope") val path: String? = null,
    /**
     * describes if there were any issues
     */
    var hasIssues: Boolean = false,
    /**
    * contains issues for the scope
    */
    @JsonProperty("issues") val issueList: IssueList = IssueList(),
    /**
     * [originalLicenses] contains the name of the original license in case of dual licensing.
     */
    val originalLicenses: String? = null
)
