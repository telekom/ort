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

import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.config.OSCakeConfigInfo

/**
 * This block contains the passed [commandLineParams] and the configuration key/value pairs in [configFile]
 */
data class ConfigBlockInfo(
    var commandLineParams: Map<String, String>,
    val configFile: Map<String, String>
    )

/**
 * The class [ConfigInfo] wraps the config information to be present in oscc file
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ConfigInfo(
    val reporter: OSCakeConfigInfo? = null
) {
    var curator: ConfigBlockInfo? = null
    var deduplicator: ConfigBlockInfo? = null
    var merger: ConfigBlockInfo? = null
    var resolver: ConfigBlockInfo? = null
    var selector: ConfigBlockInfo? = null
    var metadatamanager: ConfigBlockInfo? = null
}
