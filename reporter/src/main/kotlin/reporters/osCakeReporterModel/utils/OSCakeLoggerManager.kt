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

package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * The [OSCakeLoggerManager] is the connection to the log4j2.xml configuration file. It handles a map of different
 * [OSCakeLogger]s and acts as a factory.
 */
class OSCakeLoggerManager private constructor() {
    companion object {
        /**
         * The [logger] is a reference to the Apache log4j2.
         */
        private val logger: Logger = LogManager.getLogger()

        /**
         * [loggerMap] contains a map of different [OSCakeLogger]s.
         */
        private val loggerMap = mutableMapOf<String, OSCakeLogger>()

        /**
         * Initializes and/or returns a new [OSCakeLogger] for a given [source]
         */
        fun logger(source: String): OSCakeLogger {
            if (!loggerMap.containsKey(source)) loggerMap[source] = OSCakeLogger(source, logger)
            return loggerMap[source]!!
        }

        /**
         * Returns true, if a logger was used
         */
        fun hasLogger(source: String): Boolean = loggerMap.containsKey(source)
    }
}
