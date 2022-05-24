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
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.CompoundOrLicense
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.ProcessingPhase

/**
 * The [SelectorProvider] gets the locations where to find the yml-files containing actions (their semantics
 * is checked while processing).
 */
class SelectorProvider(val directory: File) :
    ActionProvider(directory, null, SELECTOR_LOGGER, SelectorPackage::class, ProcessingPhase.SELECTION) {

    private var errorPrefix = ""
    private val errorSuffix = " --> selector action ignored"

    override fun checkSemantics(item: ActionPackage, fileName: String, fileStore: File?): Boolean {
        item as SelectorPackage

        errorPrefix = "[Semantics] - File: $fileName [${item.id.toCoordinates()}]: "

        if (item.selectorBlocks.isEmpty()) return loggerWarn("no selector block found!")

        item.selectorBlocks.filter { !CompoundOrLicense(it.specified).isCompound }.forEach {
            return loggerWarn("specified license: \"${it.specified}\" is not a valid Compound-License! ")
        }
        item.selectorBlocks.forEach {
            if (it.specified.contains(" AND ")) {
                return loggerWarn(
                "specified license: \"${it.specified}\" contains \"AND\" - this is not a valid Compound-License, yet!"
                )
            }
        }
        item.selectorBlocks.filter { CompoundOrLicense(it.specified).isCompound }.forEach {
            if (!CompoundOrLicense(it.specified).licenseList.contains(it.selected)) {
                return loggerWarn(
                    "specified license: \"${it.selected}\" is not a valid selection of \"${it.specified}\"!"
                )
            }
        }
        return true
    }

    private fun loggerWarn(msg: String): Boolean {
        logger.log("$errorPrefix $msg $errorSuffix", Level.WARN, phase = ProcessingPhase.SELECTION)
        return false
    }
}
