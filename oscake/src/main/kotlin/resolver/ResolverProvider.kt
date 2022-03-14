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

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.oscake.RESOLVER_LOGGER
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.oscake.common.ActionProvider
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase

class ResolverProvider(val directory: File) :
    ActionProvider(directory, null, RESOLVER_LOGGER, ResolverPackage::class, ProcessingPhase.RESOLVING) {

    override fun checkSemantics(item: ActionPackage, fileName: String, fileStore: File?): Boolean {
        item as ResolverPackage
        val errorPrefix = "[Semantics] - File: $fileName [${item.id.toCoordinates()}]: "
        val errorSuffix = " --> resolver action ignored"
        val phase = ProcessingPhase.RESOLVING

        if (item.resolverBlocks.isEmpty()) {
            logger.log("$errorPrefix no resolve block found! $errorSuffix", Level.WARN, phase = phase)
            return false
        }
        item.resolverBlocks.forEach { resolverBlock ->
            // 1. licenses must contain at least one license
            if (resolverBlock.licenses.isEmpty()) {
                logger.log("$errorPrefix license list is empty! $errorSuffix", Level.WARN, phase = phase)
                return false
            }
            if (resolverBlock.licenses.any { it == null }) {
                logger.log("$errorPrefix license list contains null-values! $errorSuffix", Level.WARN, phase = phase)
                return false
            }
            // 2. result must consist of a compound license - linked with "OR"
            if (!resolverBlock.result.split(" ").contains("OR")) {
                logger.log(
                    "$errorPrefix \"result\" contains a license expression without \"OR\"! $errorSuffix",
                    Level.WARN, phase = phase
                )
                return false
            }
            // 3. scopes must contain at least one item - if not, an entry with an empty string is created
            if (resolverBlock.scopes.isEmpty()) resolverBlock.scopes.add("")
        }
        return true
    }
}
