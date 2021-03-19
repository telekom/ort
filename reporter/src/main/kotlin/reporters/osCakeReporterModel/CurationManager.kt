/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import org.apache.logging.log4j.Level
import org.ossreviewtoolkit.utils.packZip
import org.ossreviewtoolkit.utils.unpackZip
import kotlin.io.path.createTempDirectory

internal class CurationManager(val project: Project, val osCakeConfiguration: OSCakeConfiguration, val outputDir: File,
                               val reportFilename: String) {

    private val archiveDir: File by lazy {
        createTempDirectory(prefix = "oscakeCur_").toFile().apply {
            File(outputDir, project.complianceArtifactCollection.archivePath).unpackZip(this)
        }
    }
    private var curationProvider = CurationProvider(File(osCakeConfiguration.curations!!.get("directory")),
        File(osCakeConfiguration.curations.get("fileStore")))

    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(CURATION_LOGGER) }

    internal fun manage() {
        // reset "hasIssues" values
        project.hasIssues = false
        project.packs.forEach { it.hasIssues = false }

        // handle packageModifiers
        val orderByModifier = packageModifierMap.keys.withIndex().associate { it.value to it.index }
        curationProvider.packageCurations.sortedBy { orderByModifier[it.packageModifier] }.forEach { packageCuration ->
            when (packageCuration.packageModifier) {
                "insert" -> if (project.packs.none { it.id == packageCuration.id}) {
                   Pack(packageCuration.id, packageCuration.repository ?: "", "").apply {
                            project.packs.add(this)
                            reuseCompliant = checkReuseCompliance(this, packageCuration)
                        }
                    } else {
                        logger.log("Package: \"${packageCuration.id}\" already exists - no duplication!",
                            Level.INFO)
                    }
                "delete" -> deletePackage(packageCuration, archiveDir)
            }
        }

        // 2. curate each package regarding the "modifier" - insert, delete, update
        // and "packageModifier" - update, insert, delete
        project.packs.forEach {
            curationProvider.getCurationFor(it.id)?.curate(it, archiveDir,
                File(osCakeConfiguration.curations?.get("fileStore")), osCakeConfiguration)
        }

        // 3. report OSCakeIssues
        if (OSCakeLoggerManager.hasLogger(CURATION_LOGGER))
            handleOSCakeIssues(project, logger)

        // 4. generate .zip and .oscc files
        createResultingFiles()
    }

    private fun checkReuseCompliance(pack: Pack, packageCuration: PackageCuration): Boolean =
        packageCuration.curations?.any {
            it.fileScope.startsWith(getLicensesFolderPrefix(pack.packageRoot))
        } ?: false

    private fun compareLTIAwithArchive() {
        // consistency check: direction from pack to archive
        val missingFiles = mutableListOf<String>()
        project.packs.forEach { pack ->
            pack.defaultLicensings.filter { it.licenseTextInArchive != null &&
                    !File(archiveDir.path + "/" + it.licenseTextInArchive).exists() }.forEach {
                missingFiles.add(it.licenseTextInArchive.toString())
            }
            pack.reuseLicensings.filter { it.licenseTextInArchive != null &&
                    !File(archiveDir.path + "/" + it.licenseTextInArchive).exists() }.forEach {
                missingFiles.add(it.licenseTextInArchive.toString())
            }
            pack.dirLicensings.forEach { dirLicensing ->
                dirLicensing.licenses.filter { it.licenseTextInArchive != null &&
                        !File(archiveDir.path + "/" + it.licenseTextInArchive).exists() }.forEach {
                    missingFiles.add(it.licenseTextInArchive.toString())
                }
            }
            pack.fileLicensings.forEach { fileLicensing ->
                if (fileLicensing.fileContentInArchive != null && !File(archiveDir.path + "/" +
                            fileLicensing.fileContentInArchive).exists()) {
                    missingFiles.add(fileLicensing.fileContentInArchive!!)
                }
                fileLicensing.licenses.filter { it.licenseTextInArchive != null && !File(archiveDir.path +
                        "/" + it.licenseTextInArchive).exists() }.forEach {
                    missingFiles.add(it.licenseTextInArchive.toString())
                }
            }
        }
        missingFiles.forEach {
            logger.log("File: \"${it}\" not found in archive! --> Inconsistency", Level.ERROR)
        }
        // consistency check: direction from archive to pack
        missingFiles.clear()
        archiveDir.listFiles().forEach { file ->
            var found = false
            val fileName = file.name
            project.packs.forEach { pack ->
                pack.defaultLicensings.filter { it.licenseTextInArchive != null }.forEach {
                    if (it.licenseTextInArchive == fileName) found = true
                }
                pack.dirLicensings.forEach { dirLicensing ->
                    dirLicensing.licenses.filter { it.licenseTextInArchive != null }.forEach {
                        if (it.licenseTextInArchive == fileName) found = true
                    }
                }
                pack.fileLicensings.forEach { fileLicensing ->
                    if (fileLicensing.fileContentInArchive != null && fileLicensing.fileContentInArchive == fileName) {
                        found = true
                    }
                    fileLicensing.licenses.filter { it.licenseTextInArchive != null }.forEach {
                        if (it.licenseTextInArchive == fileName) found = true
                    }
                }
            }
            if (!found) missingFiles.add(fileName)
        }
        missingFiles.forEach {
            logger.log("Archived file: \"${it}\": no reference found in oscc-file! Inconsistency", Level.ERROR)
        }
    }

    /**
     * Close
     * 1. generate zip-File
     * 2. generate _curated.oscc file
     */
     fun createResultingFiles() {
        val sourceZipFileName = File(stripRelativePathIndicators(project.complianceArtifactCollection.archivePath))
        val newZipFileName = extendFilename(sourceZipFileName)
        val targetFile = File(outputDir.path, newZipFileName)

        if (archiveDir.exists()) {
            compareLTIAwithArchive()
            if (targetFile.exists()) targetFile.delete()
            archiveDir.packZip(targetFile)
            archiveDir.deleteRecursively()
        } else {
            File(outputDir, sourceZipFileName.name).copyTo(targetFile, true)
        }
        project.complianceArtifactCollection.archivePath =
            File(project.complianceArtifactCollection.archivePath).parentFile.name + "/" + newZipFileName

        // write *_curated.oscc file
        val objectMapper = ObjectMapper()
        val outputFile = outputDir.resolve(extendFilename(File(reportFilename)))
        outputFile.bufferedWriter().use {
            it.write(objectMapper.writeValueAsString(project))
        }

        println("Successfully curated the 'OSCake' at [" + outputFile + "]")
    }

    // generates new file name based on the original report file name:
    // e.g. OSCake-Report.oscc --> OSCake-Report_curated.oscc
    private fun extendFilename(reportFile: File): String = "${if (reportFile.parent != null
        ) reportFile.parent + "/" else ""}${reportFile.nameWithoutExtension}_curated.${reportFile.extension}"

    private fun stripRelativePathIndicators(name: String): String {
            if (name.startsWith("./")) return name.substring(2)
            if (name.startsWith(".\\")) return name.substring(2)
            if (name.startsWith(".")) return name.substring(1)
        return name
    }

    private fun deletePackage(packageCuration: PackageCuration, archiveDir: File) {
        val packsToDelete = mutableListOf<Pack>()
        project.packs.filter { curationProvider.getCurationFor(it.id) == packageCuration }.forEach { pack ->
            pack.fileLicensings.forEach { fileLicensing ->
                deleteFromArchive(fileLicensing.fileContentInArchive, archiveDir)
                fileLicensing.licenses.forEach {
                    deleteFromArchive(it.licenseTextInArchive, archiveDir)
                }
            }
            packsToDelete.add(pack)
        }
        project.packs.removeAll(packsToDelete)
    }
}
