/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
 * The class [OSCakeRoot] represents the root node for the output; currently, it only consists of the property
 * [Project]
 */
internal data class OSCakeRoot(private val cid: String) {
    var project: Project = Project(cid)
}
