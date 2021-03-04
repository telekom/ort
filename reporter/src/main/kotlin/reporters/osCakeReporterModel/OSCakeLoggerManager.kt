/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

internal class OSCakeLoggerManager private constructor() {
    companion object {
        val loggerMap = mutableMapOf<String, OSCakeLogger>()

        fun logger(source: String): OSCakeLogger {
            if (!loggerMap.containsKey(source)) loggerMap[source] = OSCakeLogger(source)
            return loggerMap[source]!!
        }
    }
}
