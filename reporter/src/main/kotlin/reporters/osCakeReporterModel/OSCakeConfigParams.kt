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

data class OSCakeConfigParams(
    /**
     * [ortScanResultsDir] folder where ORT stores its native scan results.
     */
    var ortScanResultsDir: String? = null,
    /**
     * if enabled, copies the ORT-native-scan-results to OSCake-native-scan-results
     */
    var scanResultsCacheEnabled: Boolean = false,
    /**
     * path, where to store the ORT-native-scan-results for caching
     */
    var oscakeScanResultsDir: String? = null,
    /**
     * [onlyIncludePackages] include only the named packages - defined as [Identifier](s) when processing.
     */
    var onlyIncludePackages: MutableMap<Identifier, Boolean> = mutableMapOf(),
    /**
     * Defines the granularity levels of the output from 0...Max
     */
    var dependencyGranularity: Int = -1,
    /**
     * deletes the ORT native-scan-results directory if set
     */
    var deleteOrtNativeScanResults: Boolean = false,
    /**
     * defines the level for issue documentation in oscc-file
     */
    var issuesLevel: Int = -1,
    /**
     * [sourceCodesDir] folders where to find or save the source code.
     */
    var sourceCodesDir: String? = null,
    var includeJsonPathInLogfile4ErrorsAndWarnings: Boolean = false,
    /**
     *  [scopePatterns] contains a list of glob patterns which are used to determine the corresponding [ScopeLevel].
     */
    var scopePatterns: List<String> = mutableListOf(),
    /**
     *  [scopePatterns] contains a list of glob patterns which are used to determine the corresponding [ScopeLevel].
     */
    var copyrightScopePatterns: List<String> = mutableListOf(),
    /**
     * [onlyIncludePackages] include the named packages - if they would be excluded by the commandline
     * option: dependency-granularity
     */
    var forceIncludePackages: MutableMap<Identifier, Boolean> = mutableMapOf(),
    /**
     *  commandline parameter defines if the licenses and copyrights should be deduplicated
     */
    var dedupLicensesAndCopyrights: Boolean = false
) {
    constructor(includeJsonPathInLogfile: Boolean) : this(
        includeJsonPathInLogfile4ErrorsAndWarnings = includeJsonPathInLogfile,
    )
}
