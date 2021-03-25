/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
 * The class [OSCakeRoot] represents the root node for the output; currently, it only consists of the property
 * [Project]
 */
internal data class OSCakeRoot(
    /**
     * The [cid] is the unique identifier for the project: e.g.: "Maven:de.tdosca.tc06:tdosca-tc06:1.0"
     */
    private val cid: String
) {
    /**
     * The [project] contains the project's packages and resolved license information.
     */
    var project: Project = Project(cid)
}
