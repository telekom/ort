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

import org.ossreviewtoolkit.oscake.RESOLVER_LOGGER
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.oscake.common.ActionProvider
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase

class ResolverProvider(val directory: File) :
    ActionProvider(directory, null, RESOLVER_LOGGER, ResolverPackage::class, ProcessingPhase.RESOLVING) {

    override fun checkSemantics(item: ActionPackage, fileName: String, fileStore: File?): Boolean {
        // todo
        item as ResolverPackage
        println(item)
        return true
    }
}
