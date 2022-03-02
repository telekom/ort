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

package org.ossreviewtoolkit.oscake.resolver

import java.io.File

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*
import org.ossreviewtoolkit.utils.core.createOrtTempFile

/**
 * A [ResolverBlock] contains one resolver-action for a specific package
 */
internal data class ResolverBlock(
    /**
     * list of licenses to use
     */
    val licenses: List<String?> = mutableListOf(),
    /**
     * resulting license = compound license info (licenses connected by "OR")
     */
    val result: String = "",
    /**
     *   list of Scopes (directory, file) - "" means the complete root directory
     */
    val scopes: MutableList<String> = mutableListOf()
)
