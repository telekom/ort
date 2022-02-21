package org.ossreviewtoolkit.oscake.resolver

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*
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

    var dedupInResolveMode: Boolean = false

   override fun process(pack: Pack, params: OSCakeConfigParams, archiveDir: File, logger: OSCakeLogger, fileStore: File?) {
       var count = 0
       val filesToDelete = mutableListOf<String>()
       val changedFileLicensings = mutableListOf<FileLicensing>()
       val licensesLower = licenses.map { it.lowercase() }.toSet()
       pack.fileLicensings.filter { it.coversOneOrAllLicenses(licensesLower) && it.fitsInPath(scopes)}
           .forEach {
               filesToDelete.addAll(it.handleCompoundLicense(result))
               changedFileLicensings.add(it)
               count++
           }
       if (count == 0) return
       pack.removeDirDefaultScopes()

       pack.createDirDefaultScopes(logger, params, ProcessingPhase.RESOLVING, true, result)

       filesToDelete.forEach {
           archiveDir.absoluteFile.resolve(it).delete()
       }

       if (dedupInResolveMode) {
           pack.deduplicateFileLicenses(archiveDir, changedFileLicensings)
           pack.deduplicateDirDirLicenses(archiveDir, true)
           pack.deduplicateDirDefaultLicenses(archiveDir, true)
       }

    }

}
