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

package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.databind.ObjectMapper

import java.io.File

import kotlin.io.path.createTempDirectory

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.utils.packZip
import org.ossreviewtoolkit.utils.unpackZip

/**
 * The [CurationManager] handles the entire curation process: reads and analyzes the curation files,
 * prepares the list of packages (passed by the reporter), applies the curations and writes the results into the output
 * files - one oscc compliant file in json format and a zip-archive containing the license texts.
 */
internal class CurationManager(
    /**
     * The [project] contains the processed output from the OSCakeReporter - specifically a list of packages.
     */
    val project: Project,
    /**
     * The generated files are stored in the folder [outputDir]
     */
    val outputDir: File,
    /**
     * The name of the reporter's output file which is extended by the [CurationManager]
     */
    val reportFilename: String) {

    /**
     * If curations have to be applied, the reporter's zip-archive is unpacked into this temporary folder.
     */
    private val archiveDir: File by lazy {
        createTempDirectory(prefix = "oscakeCur_").toFile().apply {
            File(outputDir, project.complianceArtifactCollection.archivePath).unpackZip(this)
        }
    }

    /**
     * The [curationProvider] contains a list of [PackageCuration]s to be applied.
     */
    private var curationProvider = CurationProvider(File(OSCakeConfiguration.params.curationsDirectory),
        File(OSCakeConfiguration.params.curationsFileStore))

    /**
     * The [logger] is only initialized, if there is something to log.
     */
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(CURATION_LOGGER) }

    /**
     * The method takes the reporter's output, gets rid off the reported "hasIssues", prioritizes the
     * package modifiers, applies the curations, reports emerged issues and finally, writes the output files.
     */
    internal fun manage() {
        // 1. reset "hasIssues" values and remove old issues
        project.hasIssues = false
        project.issues.warnings.clear()
        project.issues.errors.clear()
        project.issues.infos.clear()
        project.packs.forEach { pack ->
            pack.hasIssues = false
            pack.issues.warnings.clear()
            pack.issues.errors.clear()
            pack.issues.infos.clear()
            pack.defaultLicensings.forEach {
                it.hasIssues = false
                it.issues.warnings.clear()
                it.issues.errors.clear()
                it.issues.infos.clear()
            }
            pack.dirLicensings.forEach { dirLicensing ->
                dirLicensing.licenses.forEach {
                    it.issues.warnings.clear()
                    it.issues.errors.clear()
                    it.issues.infos.clear()
                    it.hasIssues = false
                }
            }
        }

        // 2. handle packageModifiers
        val orderByModifier = packageModifierMap.keys.withIndex().associate { it.value to it.index }
        curationProvider.packageCurations.sortedBy { orderByModifier[it.packageModifier] }.forEach { packageCuration ->
            when (packageCuration.packageModifier) {
                "insert" -> if (project.packs.none { it.id == packageCuration.id }) {
                   Pack(packageCuration.id, packageCuration.repository ?: "", "").apply {
                            project.packs.add(this)
                            reuseCompliant = checkReuseCompliance(this, packageCuration)
                        }
                    } else {
                        logger.log("Package: \"${packageCuration.id}\" already exists - no duplication!",
                            Level.INFO, phase = ProcessingPhase.CURATION)
                    }
                "delete" -> deletePackage(packageCuration, archiveDir)
            }
        }

        // 3. curate each package regarding the "modifier" - insert, delete, update
        // and "packageModifier" - update, insert, delete
        project.packs.forEach {
            curationProvider.getCurationFor(it.id)?.curate(it, archiveDir,
                File(OSCakeConfiguration.params.curationsFileStore))
        }

        // 4. report [OSCakeIssue]s
        if (OSCakeLoggerManager.hasLogger(CURATION_LOGGER)) handleOSCakeIssues(project, logger,
            OSCakeConfiguration.params.issuesLevel)

        // 5. generate .zip and .oscc files
        createResultingFiles()
    }

    private fun checkReuseCompliance(pack: Pack, packageCuration: PackageCuration): Boolean =
        packageCuration.curations?.any {
            it.fileScope.startsWith(getLicensesFolderPrefix(pack.packageRoot))
        } ?: false

    /**
     * The method is used for quality assurance only. It ensures that every referenced file is existing in the
     * archive and vice versa. Possible discrepancies are logged.
     */
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
            logger.log("File: \"${it}\" not found in archive! --> Inconsistency",
                Level.ERROR, phase = ProcessingPhase.CURATION)
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
            logger.log("Archived file: \"${it}\": no reference found in oscc-file! Inconsistency",
                Level.ERROR, phase = ProcessingPhase.CURATION)
        }
    }

    /**
     * The method writes the output file in oscc format (json file named  "..._curated.oscc") and creates a zip file
     * containing license text files named "..._curated.zip"
     */
    private fun createResultingFiles() {
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

    /** generates a new file name based on the original report file name: e.g.
     *  OSCake-Report.oscc --> OSCake-Report_curated.oscc
    */
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
