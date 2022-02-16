package org.ossreviewtoolkit.oscake.resolver

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.oscake.*
import org.ossreviewtoolkit.oscake.common.ActionInfo
import org.ossreviewtoolkit.oscake.common.ActionManager
import org.ossreviewtoolkit.oscake.curator.CurationManager
import org.ossreviewtoolkit.oscake.curator.CurationProvider
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.Project
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.handleOSCakeIssues
import org.ossreviewtoolkit.utils.common.unpackZip
import java.io.File
import kotlin.io.path.createTempDirectory

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
            RESOLVER_FILE_SUFFIX, config.curator?.issueLevel ?: -1), commandLineParams) {

    private var resolverProvider = ResolverProvider(File(config.resolver?.directory!!))
    /**
     * If curations have to be applied, the reporter's zip-archive is unpacked into this temporary folder.
     */
    private val archiveDir: File by lazy {
        createTempDirectory(prefix = "oscakeAct_").toFile().apply {
            File(outputDir, project.complianceArtifactCollection.archivePath).unpackZip(this)
        }
    }

    fun manage() {
        // 1. reset issues
        resetIssues()

        // 2. process (resolve, curate,..) package if it's valid
        project.packs.forEach {
            resolverProvider.getActionFor(it.id)?.process(it, setParamsforCompatibilityReasons(), null)
        }

        // 3. report [OSCakeIssue]s
        if (OSCakeLoggerManager.hasLogger(RESOLVER_LOGGER)) handleOSCakeIssues(project, logger,
            config.resolver?.issueLevel ?: -1)

        // 4. eliminate root level warnings (only warnings from reporter) when option is set
        if (commandLineParams.containsKey("ignoreRootWarnings") &&
            commandLineParams["ignoreRootWarnings"].toBoolean()) eliminateRootWarnings()

        // 5. take care of issue level settings to create the correct output format
        takeCareOfIssueLevel()

        // 6. generate .zip and .oscc files
        createResultingFiles(archiveDir)

    }
}
