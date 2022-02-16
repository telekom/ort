package org.ossreviewtoolkit.oscake.resolver

import org.ossreviewtoolkit.oscake.RESOLVER_LOGGER
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.oscake.common.ActionProvider
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase
import java.io.File

class ResolverProvider(val directory: File) :
    ActionProvider(directory, null, RESOLVER_LOGGER, ResolverPackage::class, ProcessingPhase.RESOLVING) {

    override fun checkSemantics(item: ActionPackage, fileName: String, fileStore: File?): Boolean {
        val x = item as ResolverPackage
        println(x)
        return true
    }
}
