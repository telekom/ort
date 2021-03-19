/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

internal class OSCakeLoggerManager private constructor() {
    companion object {
        val logger: Logger = LogManager.getLogger()
        private val loggerMap = mutableMapOf<String, OSCakeLogger>()

        fun logger(source: String): OSCakeLogger {
            if (!loggerMap.containsKey(source)) loggerMap[source] = OSCakeLogger(source, logger)
            return loggerMap[source]!!
        }

        fun hasLogger(source: String): Boolean = loggerMap.containsKey(source)
    }
}
