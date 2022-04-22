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

package org.ossreviewtoolkit.oscake.common

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException

import java.io.File

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Pack

/**
 * Builds the base class for OSCake-Packages.
 */
internal abstract class ActionPackage(open val id: Identifier) {
    var belongsToFile: File? = null
    /**
     * Returns true if this [ActionPackage] is applicable to the package with the given [pkgId],
     * disregarding the version.
     */
    private fun isApplicableDisregardingVersion(pkgId: Identifier) =
        id.type.equals(pkgId.type, ignoreCase = true)
                && id.namespace == pkgId.namespace
                && id.name == pkgId.name

    /**
     * Returns true if the version of this [ActionPackage] interpreted as an Ivy version matcher is applicable to the
     * package with the given [pkgId].
     */
    private fun isApplicableIvyVersion(pkgId: Identifier) =
        @Suppress("SwallowedException")
        try {
            val pkgIvyVersion = Semver(pkgId.version, Semver.SemverType.IVY)
            pkgIvyVersion.satisfies(id.version)
        } catch (e: SemverException) {
            false
        }

    /**
     * Returns true if this string equals the [other] string, or if either string is blank.
     */
    private fun String.equalsOrIsBlank(other: String) = equals(other) || isBlank() || other.isBlank()

    /**
     * Returns true if this [ActionPackage] is applicable to the package with the given [pkgId]. The
     * curation's version may be an
     * [Ivy version matcher](http://ant.apache.org/ivy/history/2.4.0/settings/version-matchers.html).
     */
    internal fun isApplicable(pkgId: Identifier): Boolean =
        isApplicableDisregardingVersion(pkgId)
                && (id.version.equalsOrIsBlank(pkgId.version) || isApplicableIvyVersion(pkgId))

    /**
     * [process] has to be overridden by the child classes and contains the complete application logic
     */
    abstract fun process(pack: Pack, archiveDir: File, logger: OSCakeLogger, fileStore: File? = null)
}
