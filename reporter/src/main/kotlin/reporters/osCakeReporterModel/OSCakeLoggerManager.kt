/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * The [OSCakeLoggerManager] is the connection to the log4j2.xml configuration file. It handles a map of different
 * [OSCakeLogger]s and acts as a factory.
 */
internal class OSCakeLoggerManager private constructor() {
    companion object {
        /**
         * The [logger] is a reference to the Apache log4j2.
         */
        val logger: Logger = LogManager.getLogger()

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
