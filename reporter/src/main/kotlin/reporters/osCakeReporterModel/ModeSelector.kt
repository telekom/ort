/*
 * Copyright (C) 2021 Deutsche Telekom AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import java.io.File

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.reporter.ReporterInput

/**
 * The [ModeSelector] implements a factory depending on the type of package and distinguishes between
 * REUSE-compliant and non-REUSE-compliant packages
 * */
internal abstract class ModeSelector {
    /**
     * The method processes the packages and fetches infos from the scanner output
     */
    internal abstract fun fetchInfosFromScanDictionary(sourceCodeDir: String?, tmpDirectory: File)

    /**
     * Defines program steps after terminating the [fetchInfosFromScanDictionary] method
     */
    internal abstract fun postActivities()

    /**
     * The [logger] is only initialized, if there is something to log.
     */
    internal val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(REPORTER_LOGGER) }

    companion object {
        /**
         * The [getMode] method returns an instance of [ModeREUSE] or [ModeDefault] depending on the type
         * of package
         */
        internal fun getMode(pack: Pack, scanDict: MutableMap<Identifier, MutableMap<String, FileInfoBlock>>,
                             osCakeConfiguration: OSCakeConfiguration, reporterInput: ReporterInput): ModeSelector {
            return when (pack.reuseCompliant) {
                true -> ModeREUSE(pack, scanDict)
                else -> ModeDefault(pack, scanDict, osCakeConfiguration, reporterInput)
            }
        }
    }
}
