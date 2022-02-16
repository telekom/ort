package org.ossreviewtoolkit.oscake.resolver

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeConfigParams
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Pack
import java.io.File

data class ResolverPackage(
    /**
    * The [id] contains a package identification as defined in the [Identifier] class. The version information may
    * be stored as a specific version number, an IVY-expression (describing a range of versions) or may be empty (then
    * it will be valid for all version numbers).
    */
    override val id: Identifier,
    /**
    *
    */
   val licenses: List<String> = mutableListOf(),
    /**
    *
    */
   val result: String = "",
    /**
    *
    */
   val scopes: List<String> = mutableListOf()
) : ActionPackage(id) {

   override fun process(pack: Pack, params: OSCakeConfigParams, archiveDir: File, fileStore: File?) {

       pack.fileLicensings.forEach {
            if (it.coversOneOrAllLicenses(licenses) && fitsInPath(it.scope))
                println("YEAH!")
       }
       println("---> Processing")
    }

    private fun fitsInPath(fileScope: String): Boolean {
        if (scopes.contains(fileScope)) return true
        if (scopes.any { it.startsWith(File(fileScope).path) }) return true
        if (scopes.contains("")) return true
        return false
    }

}
