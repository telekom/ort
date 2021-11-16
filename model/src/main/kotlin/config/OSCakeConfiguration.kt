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
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The base configuration model of the oscake applications.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OSCakeConfiguration(
    val oscakeCurations: OSCakeCurations? = null
)

data class OSCakeCurations(
    val directory: String? = null,
    val fileStore: String? = null
)
