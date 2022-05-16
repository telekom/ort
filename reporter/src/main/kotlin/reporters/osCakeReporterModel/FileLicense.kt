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
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * The FileLicense class wraps the information about the [license] and the name of the file containing the license
 * information [licenseTextInArchive]. An instance with null values may exist if the file was archived by the scanner
 * and not treated by other logic branches
 */
@JsonPropertyOrder("license", "originalLicenses", "licenseTextInArchive")
data class FileLicense(
    /**
     * [license] contains the name of the license.
     */
    var license: String?,
    /**
     * Represents the path to the file containing the license text in the archive.
     */
    var licenseTextInArchive: String? = null,
    /**
     * [startLine] indicates the first line of the license statement within the source file. It is used to
     * distinguish between different license findings in the same file.
     */
    @JsonIgnore var startLine: Int = -1,
    /**
     * [originalLicenses] contains the name of the original license in case of dual licensing.
     */
    @get: JsonInclude(JsonInclude.Include.NON_NULL) var originalLicenses: String? = null
)
