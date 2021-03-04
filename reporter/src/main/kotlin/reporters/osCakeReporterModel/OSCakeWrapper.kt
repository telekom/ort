/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
    Wrapper class for the [OSCakeConfiguration] class - reads the file passed by option "OSCake=configFile=..:"
 */
@Suppress("ConstructorParameterNaming")
internal data class OSCakeWrapper(val OSCake: OSCakeConfiguration)
