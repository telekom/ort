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
 * A class representing a license item for REUSE packages.
 */
@JsonPropertyOrder("foundInFileScope", "license", "licenseTextInArchive")
class ReuseLicense(
    /**
     * [license] keeps the name of the license.
     */
    val license: String?,
    /**
     * Path to the corresponding file (source).
     */
    @get:JsonProperty("foundInFileScope") val path: String?,
    /**
     * Path to the license file in the archive (target)
     */
    var licenseTextInArchive: String? = null
)
