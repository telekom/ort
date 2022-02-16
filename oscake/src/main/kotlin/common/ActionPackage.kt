package org.ossreviewtoolkit.oscake.common

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.oscake.curator.PackageCuration
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeConfigParams
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Pack
import java.io.File

abstract class ActionPackage(open val id: Identifier) {

    /**
     * Returns true if this [PackageCuration] is applicable to the package with the given [pkgId],
     * disregarding the version.
     */
    fun isApplicableDisregardingVersion(pkgId: Identifier) =
        id.type.equals(pkgId.type, ignoreCase = true)
                && id.namespace == pkgId.namespace
                && id.name == pkgId.name

    /**
     * Returns true if the version of this [PackageCuration] interpreted as an Ivy version matcher is applicable to the
     * package with the given [pkgId].
     */
    fun isApplicableIvyVersion(pkgId: Identifier) =
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
    fun String.equalsOrIsBlank(other: String) = equals(other) || isBlank() || other.isBlank()

    /**
     * Return true if this [PackageCuration] is applicable to the package with the given [pkgId]. The
     * curation's version may be an
     * [Ivy version matcher](http://ant.apache.org/ivy/history/2.4.0/settings/version-matchers.html).
     */
    fun isApplicable(pkgId: Identifier): Boolean =
        isApplicableDisregardingVersion(pkgId)
                && (id.version.equalsOrIsBlank(pkgId.version) || isApplicableIvyVersion(pkgId))

    abstract fun process(pack: Pack, params: OSCakeConfigParams, archiveDir: File? = null)



}
