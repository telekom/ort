/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * The base configuration model of the oscake applications.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OSCakeConfiguration(
    val curator: OSCakeCurator? = null,
    val deduplicator: OSCakeDeduplicator? = null,
    val resolver: OSCakeResolver? = null,
    val selector: OSCakeSelector? = null,
    val metadatamanager: OSCakeMetaDataManager? = null,
    val prettyPrint: Boolean? = false
)

data class OSCakeDeduplicator(
    val keepEmptyScopes: Boolean? = false,
    val createUnifiedCopyrights: Boolean? = false,
    val preserveFileScopes: Boolean? = true,
    val compareOnlyDistinctLicensesCopyrights: Boolean? = false,
    val processPackagesWithIssues: Boolean? = true,
    val hideSections: List<String>? = emptyList()
)

data class OSCakeCurator(
    /**
     * The [directory] where to find the yml files to be used for curations
     */
    val directory: String? = null,
    /**
     * The directory where to find referenced files in curations (*.yml files)
     */
    val fileStore: String? = null,
    /**
     * [issueLevel]: -1..not enabled, 0..ERROR, 1..WARN + ERROR, 2..INFO + WARN + ERROR
     */
    val issueLevel: Int? = -1
)

data class OSCakeResolver(
    /**
     * The [directory] where to find the yml files to be used for resolving
     */
    val directory: String? = null,
    /**
     * [issueLevel]: -1..not enabled, 0..ERROR, 1..WARN + ERROR, 2..INFO + WARN + ERROR
     */
    val issueLevel: Int? = -1,
)

data class OSCakeSelector(
    /**
     * The [directory] where to find the yml files to be used for resolving
     */
    val directory: String? = null,
    /**
     * [issueLevel]: -1..not enabled, 0..ERROR, 1..WARN + ERROR, 2..INFO + WARN + ERROR
     */
    val issueLevel: Int? = -1,
)

data class OSCakeMetaDataManager(
    /**
     * The [distribution] information
     */
    val distribution: OSCakeMetaDataManagerDistribution? = null,
    /**
     * The [packageType] information
     */
    val packageType: OSCakeMetaDataManagerPackageType? = null,
    /**
     * [issueLevel]: -1..not enabled, 0..ERROR, 1..WARN + ERROR, 2..INFO + WARN + ERROR
     */
    val issueLevel: Int? = -1,
)
data class OSCakeMetaDataManagerDistribution(
    /**
     * mechanism enabled?
     */
    val enabled: Boolean? = false,
    /**
     * The [directory] where to find the yml files to be used for distribution
     */
    val directory: String? = null
)
data class OSCakeMetaDataManagerPackageType(
    /**
     * mechanism enabled?
     */
    val enabled: Boolean? = false,
    /**
     * The [directory] where to find the yml files to be used for changing the package type
     */
    val directory: String? = null
)
