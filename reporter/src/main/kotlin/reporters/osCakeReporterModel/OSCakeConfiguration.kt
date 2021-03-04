/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
Wrapper class for the [OSCakeConfiguration] class - reads the file passed by option "OSCake=configFile=..:"
 */
internal data class OSCakeConfiguration(
    val scopePatterns: List<String> = mutableListOf<String>(),
    val curations: Map<String, String>? = null
)
