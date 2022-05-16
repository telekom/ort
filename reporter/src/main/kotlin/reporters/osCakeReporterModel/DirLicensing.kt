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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * The class DirLicensing is a collection of [DirLicense] instances for the given path (stored in [scope])
 */
@JsonPropertyOrder("dirScope", "dirLicenses", "dirCopyrights")
data class DirLicensing(
    /**
     * [scope] contains the name of the folder to which the licenses belong.
     */
    @get:JsonProperty("dirScope") val scope: String
) {
    /**
     * [licenses] contains a list of [DirLicense]s.
     */
    @JsonProperty("dirLicenses") val licenses = mutableListOf<DirLicense>()
    /**
     * [copyrights] contains a list of [DefaultDirCopyright]s.
     */
    @JsonProperty("dirCopyrights") val copyrights = mutableListOf<DefaultDirCopyright>()
}
