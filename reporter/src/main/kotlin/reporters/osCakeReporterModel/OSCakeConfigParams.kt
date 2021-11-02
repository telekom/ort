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

import org.ossreviewtoolkit.model.Identifier

/**
Wrapper class for the [OSCakeConfigParams] class - used to store processed data from [OSCakeConfiguration]
 */

internal data class OSCakeConfigParams(
    /**
     * [ortScanResultsDir] folder where ORT stores its native scan results.
     */
    val ortScanResultsDir: String? = null,
    /**
     * if enabled, copies the ORT-native-scan-results to OSCake-native-scan-results
     */
    val scanResultsCacheEnabled: Boolean = false,
    /**
     * path, where to store the ORT-native-scan-results for caching
     */
    val oscakeScanResultsDir: String? = null,
    /**
     * [onlyIncludePackages] include only the named packages - defined as [Identifier](s) when processing.
     */
    val onlyIncludePackages: MutableMap<Identifier, Boolean> = mutableMapOf(),
    /**
     * Defines the granularity levels of the output from 0...Max
     */
    val dependencyGranularity: Int,
    /**
     * deletes the ORT native-scan-results directory if set
     */
    val deleteOrtNativeScanResults: Boolean,
    /**
     * defines the level for issue documentation in oscc-file
     */
    val issuesLevel: Int
)
