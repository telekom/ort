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

/**
 * A class defining a curation for a license.
 */
internal data class CurationFileLicenseItem(
    /**
     * The [modifier] defines the application of the curation: delete, insert or update.
     */
    val modifier: String,
    /**
     * The optional [reason] is a description of the necessity of the curation.
     */
    val reason: String?,
    /**
     * The [license] is used to identify the specific license text; it may contain a valid license string,
     * a placeholder "*" or null (null is treated like a specific license in order to curate license text entries with
     * null.
     */
    val license: String?,
    /**
     * [licenseTextInArchive] represents a relative path to the file containing the license information in the archive;
     * it also may consist of a placeholder "*" for any file or null for no file at all.
     */
    @JsonProperty("license_text_in_archive") val licenseTextInArchive: String?
)
