/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
Wrapper class for the [OSCakeConfiguration] class - reads the file passed by option "OSCake=configFile=..:"
 */
internal data class OSCakeConfiguration(
    /**
     *  [scopePatterns] contains a list of glob patterns which are used to determine the corresponding [ScopeLevel].
     */
    val scopePatterns: List<String> = mutableListOf<String>(),
    /**
     * [curations] contains information about the folders where the curation files can be found.
     */
    val curations: Map<String, String>? = null
)
