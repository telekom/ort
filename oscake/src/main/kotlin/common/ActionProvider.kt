package org.ossreviewtoolkit.oscake.common

import org.apache.logging.log4j.Level
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.oscake.curator.PackageCuration
import org.ossreviewtoolkit.oscake.resolver.ResolverPackage
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase
import java.io.File
import java.io.IOException

abstract class ActionProvider(directory: File, fileStore: File?, loggerName: String, clazz: Any, pphase: ProcessingPhase) {

    internal val actions = mutableListOf<ActionPackage>()
    /**
     * The [logger] is only initialized, if there is something to log.
     */
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(loggerName) }
    private val phase = pphase

    companion object {
        var errors = false
    }

    /**
     * The init method walks through the folder hierarchy - starting at "curationDirectory" - and creates a list
     * of "PackageCurations".
     */
    init {
        if (directory.isDirectory) {
            directory.walkTopDown().filter { it.isFile && it.extension == "yml" }.forEach { file ->
                try {
                    var ymls = listOf<ActionPackage>()
                    if (clazz == ResolverPackage::class) {
                        ymls = file.readValue<List<ResolverPackage>>()
                    }
                    ymls.forEach {
                        if (checkSemantics(it, file.name, fileStore)) actions.add(it)
                    }
                } catch (e: IOException) {
                    logger.log("Error while processing file: ${file.absoluteFile}! - Action not applied! ${e.message}",
                        Level.ERROR, phase = phase
                    )
                    errors = true
                }
            }
        }
    }

    abstract fun checkSemantics(item: ActionPackage, fileName: String, fileStore: File?): Boolean

    /**
     * Returns the [ActionPackage] which is applicable for a specific package Id or null if there is none or
     * more than one.
     */
    internal fun getActionFor(pkgId: Identifier): ActionPackage? {
        actions.filter { it.isApplicable(pkgId) }.apply {
            if (size > 1) logger.log("Error: more than one action was found for" +
                    " package: $pkgId - don't know which one to take!", Level.ERROR, pkgId,
                phase = phase
            )
            if (size != 1) return null
            return first()
        }
    }

}
