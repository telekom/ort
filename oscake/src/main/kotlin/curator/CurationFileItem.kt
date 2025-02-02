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

package org.ossreviewtoolkit.oscake.curator

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A class defining curations for licenses and copyrights for a specific file defined in [fileScope].
 */
internal data class CurationFileItem(
    /**
     * Relative path to the specified file - may also contain the "packageRoot" as defined in the class pack.
     */
    @JsonProperty("file_scope") val fileScope: String,
    /**
     * List of [fileLicenses] to be processed.
     */
    @JsonProperty("file_licenses") val fileLicenses: List<CurationFileLicenseItem>?,
    /**
     * List of [fileCopyrights] to be processed.
     */
    @JsonProperty("file_copyrights") val fileCopyrights: List<CurationFileCopyrightItem>?
)
