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

package org.ossreviewtoolkit.oscake.selector

import java.io.File

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.oscake.SELECTOR_LOGGER
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.oscake.common.ActionProvider
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.CompoundOrLicense
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase

/**
 * The [SelectorProvider] gets the locations where to find the yml-files containing actions (their semantics
 * is checked while processing).
 */
class SelectorProvider(val directory: File) :
    ActionProvider(directory, null, SELECTOR_LOGGER, SelectorPackage::class, ProcessingPhase.SELECTION) {

    override fun checkSemantics(item: ActionPackage, fileName: String, fileStore: File?): Boolean {
        item as SelectorPackage
        val errorPrefix = "[Semantics] - File: $fileName [${item.id.toCoordinates()}]: "
        val errorSuffix = " --> selector action ignored"
        val phase = ProcessingPhase.SELECTION

        if (item.selectorBlocks.isEmpty()) {
            logger.log("$errorPrefix no resolve block found! $errorSuffix", Level.WARN, phase = phase)
            return false
        }
        item.selectorBlocks.forEach {
            if (!CompoundOrLicense(it.specified).isCompound) {
                logger.log(
                    "$errorPrefix specified license: \"${it.specified}\" is not a valid Compound-License! " +
                            errorSuffix, Level.WARN, phase = phase
                )
                return false
            }
        }
        item.selectorBlocks.forEach {
            if (it.specified.contains(" AND ")) {
                logger.log(
                    "$errorPrefix specified license: \"${it.specified}\" contains \"AND\" - this is not a " +
                            "valid Compound-License, yet! " + errorSuffix, Level.WARN, phase = phase
                )
                return false
            }
        }
        item.selectorBlocks.filter { CompoundOrLicense(it.specified).isCompound }.forEach {
            val cl = CompoundOrLicense(it.specified)
            if (!cl.licenseList.contains(it.selected)) {
                logger.log(
                    "$errorPrefix specified license: \"${it.selected}\" is not a valid selection " +
                            "of \"${it.specified}\"! $errorSuffix", Level.WARN, phase = phase
                )
                return false
            }
        }
        return true
    }
}
