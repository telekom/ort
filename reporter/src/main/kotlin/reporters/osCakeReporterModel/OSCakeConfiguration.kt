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

/**
Wrapper class for the [OSCakeConfiguration] class - reads the file passed by option "OSCake=configFile=...:"
 */
internal data class OSCakeConfiguration(
    /**
     *  [scopePatterns] contains a list of glob patterns which are used to determine the corresponding [ScopeLevel].
     */
    val scopePatterns: List<String> = mutableListOf(),
    /**
     * [curations] contains information about the folders where the curation files can be found.
     */
    val curations: Map<String, String>? = null,
    /**
     * [sourceCodesDir] folders where to find or save the source code.
     */
    val sourceCodesDir: String? = null,
    /**
     * [scanResultsCache] holds information on how to handle scan-results.
     */
    val scanResultsCache: Map<String, String>? = null,
    /**
     * [ortScanResultsDir] folder where ORT stores its native scan results.
     */
    val ortScanResultsDir: String? = null,
)
