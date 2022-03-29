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

package org.ossreviewtoolkit.oscake.injector

import java.io.File

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.oscake.INJECTOR_LOGGER
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.oscake.common.ActionProvider
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase

/**
 * The [PackageTypeProvider] gets the locations where to find the yml-files containing actions (their semantics
 * is checked while processing).
 */
class PackageTypeProvider(val directory: File) :
    ActionProvider(directory, null, INJECTOR_LOGGER, PackageTypePackage::class, ProcessingPhase.INJECTION) {

    override fun checkSemantics(item: ActionPackage, fileName: String, fileStore: File?): Boolean {
        item as PackageTypePackage
        val errorPrefix = "[Semantics] - File: $fileName [${item.id.toCoordinates()}]: "
        val errorSuffix = " --> distributor action ignored"
        val phase = ProcessingPhase.INJECTION

        if (item.packageTypeBlock.from == item.packageTypeBlock.to) {
            logger.log("$errorPrefix \"from\" and \"to\" are equal! $errorSuffix", Level.WARN, phase = phase)
            return false
        }
        return true
    }
}
