package org.ossreviewtoolkit.oscake.resolver

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.logging.log4j.Level
import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.*
import org.ossreviewtoolkit.oscake.common.ActionInfo
import org.ossreviewtoolkit.oscake.common.ActionManager
import org.ossreviewtoolkit.oscake.curator.CurationManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Project
import org.ossreviewtoolkit.utils.common.unpackZip
import java.io.File
import java.util.*
import kotlin.io.path.createTempDirectory

data class AnalyzerLicenses (
    val declared_licenses: SortedSet<String>,
    val declared_licenses_processed: String)

class ResolverManager(
    /**
       * The [project] contains the processed output from the OSCakeReporter - specifically a list of packages.
       */
    override val project: Project,
  /**
       * The generated files are stored in the folder [outputDir]
       */
  override val outputDir: File,
  /**
       * The name of the reporter's output file which is extended by the [CurationManager]
       */
    override val reportFilename: String,
  /**
       * Configuration in ort.conf
       */
    override val config: OSCakeConfiguration,

    override val commandLineParams: Map<String, String>

) : ActionManager(project, outputDir, reportFilename, config,
        ActionInfo(RESOLVER_LOGGER, ProcessingPhase.RESOLVING, RESOLVER_AUTHOR, RESOLVER_VERSION, "Resolver",
            RESOLVER_FILE_SUFFIX, config.resolver?.issueLevel ?: -1), commandLineParams) {

    private var resolverProvider = ResolverProvider(File(config.resolver?.directory!!))
    /**
     * If curations have to be applied, the reporter's zip-archive is unpacked into this temporary folder.
     */
    private val archiveDir: File by lazy {
        createTempDirectory(prefix = "oscakeAct_").toFile().apply {
            File(outputDir, project.complianceArtifactCollection.archivePath).unpackZip(this)
        }
    }
    private val analyzedPackageLicenses = fetchPackageLicensesFromAnalyzer()

    fun manage() {
        // 1. reset issues
        resetIssues()

        // 2. create resolving actions for packages with a [DECLARED] license
        project.packs.filter { pack -> pack.defaultLicensings.any { it.license == FOUND_IN_FILE_SCOPE_DECLARED } }
            .forEach { pack -> appendAction(pack)
        }

        // 3. process (resolve, curate,..) package if it's valid
        project.packs.forEach {
            resolverProvider.getActionFor(it.id)?.process(it, setParamsforCompatibilityReasons(), archiveDir)
        }

        // 4. report [OSCakeIssue]s
        if (OSCakeLoggerManager.hasLogger(RESOLVER_LOGGER)) handleOSCakeIssues(project, logger,
            config.resolver?.issueLevel ?: -1)

        // 5. eliminate root level warnings (only warnings from reporter) when option is set
        if (commandLineParams.containsKey("ignoreRootWarnings") &&
            commandLineParams["ignoreRootWarnings"].toBoolean()) eliminateRootWarnings()

        // 6. take care of issue level settings to create the correct output format
        takeCareOfIssueLevel()

        // 7. generate .zip and .oscc files
        createResultingFiles(archiveDir)

    }

    private fun appendAction(pack: Pack) = analyzedPackageLicenses[pack.id]?.let {
            resolverProvider.actions.add(ResolverPackage(pack.id, it.declared_licenses.toList(), it.declared_licenses_processed, listOf("")))
        }

    private fun fetchPackageLicensesFromAnalyzer() : Map<Identifier, AnalyzerLicenses> {
        val licMap : MutableMap<Identifier, AnalyzerLicenses> = emptyMap<Identifier, AnalyzerLicenses>().toMutableMap()

        if(commandLineParams.containsKey("analyzerFile") && File(commandLineParams["analyzerFile"]).exists()) {
            val ortResult = File(commandLineParams["analyzerFile"]).readValueOrNull<OrtResult>()

            ortResult?.analyzer?.result?.projects?.forEach { project ->
                if (isValidLicense(project.declaredLicensesProcessed.spdxExpression.toString()))
                    licMap[project.id] = AnalyzerLicenses(project.declaredLicenses, project.declaredLicensesProcessed.spdxExpression.toString())
            }
            ortResult?.analyzer?.result?.packages?.filter { !ortResult.isExcluded(it.pkg.id) }?.forEach { it ->
                if (isValidLicense( it.pkg.declaredLicensesProcessed.spdxExpression.toString()))
                    licMap[it.pkg.id] = AnalyzerLicenses(it.pkg.declaredLicenses, it.pkg.declaredLicensesProcessed.spdxExpression.toString())
            }
        } else
            logger.log("Results from ORT-Analyzer not found or not provided!", Level.INFO, phase = ProcessingPhase.RESOLVING  )
        return licMap
    }

    private fun isValidLicense(declaredLicense: String): Boolean = declaredLicense.contains(" OR ")
            && !declaredLicense.contains(" AND ") && !declaredLicense.contains(" WITH ")

}
