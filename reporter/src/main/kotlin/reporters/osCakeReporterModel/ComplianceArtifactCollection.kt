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
import com.fasterxml.jackson.annotation.JsonPropertyOrder

import java.time.LocalDateTime

/**
 * The class [ComplianceArtifactCollection] contains meta information about the collection of Packages in an oscc file.
 * Currently, only zip archives are supported.
 */
@JsonPropertyOrder("cid", "author", "release", "date", "archivePath", "archiveType", "mergedIds")
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
data class ComplianceArtifactCollection(
    /**
     * [cid] is the Identifier of this project e.g.: "Maven:de.tdosca.tc06:tdosca-tc06:1.0".
     */
    var cid: String = "",
    /**
     * Responsible for the content
     */
    var author: String = "OSCake-Reporter",
    /**
     * Represents the release number of the OSCakeReporter which was used when creating the file.
     */
    var release: String = "0.1",
    /**
     * [date] keeps the creation timestamp of the report.
     */
    var date: String = LocalDateTime.now().toString(),
    /**
     * [archivePath] describes the path to the archive file containing the processed license files.
     */
    var archivePath: String = "./licensefiles.zip",
    /**
     * In current versions only zip files are used.
     */
    var archiveType: String = "ZIP"
) {
    /**
     * used for OSCakeMerger: contains list of CIDs which are merged
     */
    var mergedIds: MutableList<String> = mutableListOf()
}
