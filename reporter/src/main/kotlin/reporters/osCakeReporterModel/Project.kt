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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Enumeration
import java.util.Locale
import java.util.zip.ZipEntry

import kotlin.system.exitProcess

import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveOutputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.IOUtils
import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier

/**
 * The class [Project] wraps the meta information ([complianceArtifactCollection]) of the OSCakeReporter as well
 * as a list of included projects and packages store in instances of [Pack]
 */
@JsonPropertyOrder("hasIssues", "containsHiddenSections", "issues", "config", "complianceArtifactCollection",
    "complianceArtifactPackages")
@JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = IssuesFilterCustom::class)
data class Project(
    /**
     * [hasIssues] shows if problems occurred during processing the data.
     */
    var hasIssues: Boolean = false,
    /**
     * [containsHiddenSections] shows if sections are missing in oscc
     */
    @JsonInclude(JsonInclude.Include.NON_NULL) var containsHiddenSections: Boolean? = null,
    /**
     * contains issues for the project level
     */
    @get:JsonProperty("issues") val issueList: IssueList = IssueList(),
    /**
     * contains the runtime configuration - commandline parameters and oscake.conf
     */
    var config: ConfigInfo? = null,
    /**
     * [complianceArtifactCollection] contains metadata about the project.
     */
    val complianceArtifactCollection: ComplianceArtifactCollection = ComplianceArtifactCollection(),
    /**
     * [packs] is a list of packages [Pack] which are part of the project.
     */
    @get:JsonProperty("complianceArtifactPackages") val packs: MutableList<Pack> = mutableListOf()
) {
    companion object {
        private lateinit var zipOutput: ArchiveOutputStream
        private lateinit var zipOutputStream: FileOutputStream
        private var initProject: Project? = null
        private var archiveFile: File? = null
        private lateinit var logger: OSCakeLogger

        // see https://docs.oracle.com/javase/9/docs/specs/security/standard-names.html#messagedigest-algorithms
        // used to create unique hashes as prefixes for file references
        private val DIGEST by lazy { MessageDigest.getInstance("SHA-1") }

        // initialize a new project - used as the target for the merged projects
        fun initialize(cac: ComplianceArtifactCollection, arcFile: File, oscakeLogger: OSCakeLogger): Project {
            if (initProject != null) return initProject!!

            logger = oscakeLogger
            initProject = Project(complianceArtifactCollection = cac)
            initProject!!.isInitialProject = true
            archiveFile = arcFile
            try {
                zipOutputStream = FileOutputStream(arcFile)
                zipOutput = ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.ZIP, zipOutputStream)
            } catch (ex: ArchiveException) {
                logger.log("Error when handling the archive file <$archiveFile> - Abnormal program termination! " +
                        ex.toString(), Level.ERROR, phase = ProcessingPhase.MERGING)
                exitProcess(2)
            }

            return initProject!!
        }

        fun terminateArchiveHandling() {
            if (initProject != null) {
                try {
                    zipOutput.finish()
                    zipOutputStream.close()
                } catch (ex: ArchiveException) {
                    logger.log("Error when terminating the archive file <$archiveFile> handling - Abnormal " +
                            "program termination! " + ex.toString(), Level.ERROR, phase = ProcessingPhase.MERGING)
                    exitProcess(2)
                }
            }
        }
        fun osccToModel(osccFile: File, logger: OSCakeLogger, processingPhase: ProcessingPhase): Project {
            val mapper = jacksonObjectMapper()
            var project: Project? = null
            try {
                val json = osccFile.readText()
                project = mapper.readValue<Project>(json)
            } catch (e: IOException) {
                logger.log("EXIT: Invalid json format found in file: \"$osccFile\".\n ${e.message} ",
                    Level.ERROR, phase = processingPhase)
                exitProcess(3)
            } finally {
                require(project != null) { "Project could not be initialized when reading: \"$osccFile\"" }
                completeModel(project)
            }
            return project
        }
        // rebuild info for model completeness built on information in oscc
        private fun completeModel(project: Project) {
            project.packs.forEach { pack ->
                pack.namespace = pack.id.namespace
                pack.type = pack.id.type
                pack.defaultLicensings.forEach {
                    it.declared = it.path == FOUND_IN_FILE_SCOPE_DECLARED
                }
                pack.reuseCompliant = pack.reuseLicensings.isNotEmpty()
            }
        }
    }

    // shows that this project is the target project
    private var isInitialProject = false

    /**
     * Writes the model to disk (in json format)
     */
    fun modelToOscc(outputFile: File, logger: OSCakeLogger, processingPhase: ProcessingPhase): Boolean {
        val objectMapper = ObjectMapper()
        try {
            outputFile.bufferedWriter().use {
                it.write(objectMapper.writeValueAsString(this))
            }
        } catch (e: IOException) {
            logger.log("Error when writing json file: \"$outputFile\".\n ${e.message} ",
                Level.ERROR, phase = processingPhase)
            return true
        }
        return false
    }

    @Suppress("ReturnCount")
    /**
     * merges the given project into the initialProject
     */
    fun merge(project: Project, originFile: File, prohibitedAuthor: String): Boolean {
        // merge only for [initialized] project allowed
        if (!isInitialProject) return false
        // do not process, if definitions in [complianceArtifactCollection] or itself are missing
        if (project.complianceArtifactCollection.cid == "") return false
        if (project.complianceArtifactCollection.author == prohibitedAuthor) {
            logger.log("The file \"${originFile.name}\" cannot be processed, because it was already deduplicated" +
                    " in a former run!", Level.WARN, phase = ProcessingPhase.MERGING)
            return false
        }
        if (project.containsHiddenSections == true) {
            logger.log("The file \"${originFile.name}\" cannot be processed, because some sections are missing!" +
                    " (maybe it was created with config option \"hideSections\")", Level.ERROR,
                phase = ProcessingPhase.MERGING)
            return false
        }

        val packagesToAdd = mutableListOf<Pack>()
        val filesToArchive = mutableListOf<String>()
        val prefix = getNewPrefix(project)
        val absoluteFilePathToZip: File?
        val originDir = originFile.parentFile

        if (project.hasIssues) {
            logger.log("<${originFile.name}> contains unresolved issues - not processed!", Level.ERROR,
                phase = ProcessingPhase.MERGING)
            hasIssues = true
            return false
        }

        if (project.complianceArtifactCollection.cid == "") {
            logger.log("Incomplete <complianceArtifactCollection> in file: <${originFile.name}>", Level.ERROR,
                phase = ProcessingPhase.MERGING)
            hasIssues = true
            return false
        } else {
            absoluteFilePathToZip = originDir.resolve(project.complianceArtifactCollection.archivePath)
            if (!absoluteFilePathToZip.exists()) {
                logger.log("Archive file <$absoluteFilePathToZip> for project in <${originFile.name}> does " +
                        "not exist!", Level.ERROR, phase = ProcessingPhase.MERGING)
                hasIssues = true
                return false
            }
        }

        project.packs.forEach { complianceArtifactPackage ->
            if (!containsID(complianceArtifactPackage.id)) {
                // remove old issues, only info-issues remains
                complianceArtifactPackage.issueList.warnings.clear()
                complianceArtifactPackage.issueList.errors.clear()
                packagesToAdd.add(complianceArtifactPackage)
                adjustFilePaths(complianceArtifactPackage, prefix, filesToArchive)
            } else {
                val ins = inspectPackage(complianceArtifactPackage)
                hasIssues = hasIssues || ins
                return !ins
            }
        }
        if (filesToArchive.size > 0) {
            if (!copyFromArchiveToArchive(filesToArchive, prefix, absoluteFilePathToZip)) hasIssues = true
        }
        if (packagesToAdd.size > 0) packs.addAll(packagesToAdd)

        return true
    }

    /**
     * Copies files from the source archive to the target archive by renaming each file (prepending the [prefix])
     */
    @Suppress("NestedBlockDepth")
    private fun copyFromArchiveToArchive(
        filesToArchive: List<String>,
        prefix: String,
        absoluteFilePathToZip: File
    ): Boolean {
        var rc = true
        try {
            val zipInput = ZipFile(absoluteFilePathToZip)
            zipInput.use { zip ->
                val entries: Enumeration<ZipArchiveEntry> = zip.entries
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!filesToArchive.contains(entry.name)) continue
                    val newEntry = ZipEntry(prefix + entry.name)
                    newEntry.method = entry.method
                    zipOutput.putArchiveEntry(ZipArchiveEntry(newEntry))
                    IOUtils.copy(zipInput.getInputStream(entry), zipOutput)
                    zipOutput.closeArchiveEntry()
                }
            }
        } catch (ex: ArchiveException) {
            logger.log("Error when copying zip file from <$absoluteFilePathToZip> to <${archiveFile!!.name}>: " +
                    ex.toString(), Level.ERROR, phase = ProcessingPhase.MERGING)
            rc = false
        }
        return rc
    }

    /**
     * Adjusts all file paths in the file references - prepends the names with a prefix.
     * */
    private fun adjustFilePaths(
        complianceArtifactPackage: Pack,
        prefix: String,
        filesToArchive: MutableList<String>
    ) {
        complianceArtifactPackage.defaultLicensings.forEach {
            if (it.licenseTextInArchive != null) {
                filesToArchive.add(it.licenseTextInArchive!!)
                it.licenseTextInArchive = "$prefix${it.licenseTextInArchive}"
            }
        }
        complianceArtifactPackage.reuseLicensings.forEach {
            if (it.licenseTextInArchive != null) {
                filesToArchive.add(it.licenseTextInArchive!!)
                it.licenseTextInArchive = "$prefix${it.licenseTextInArchive}"
            }
        }
        complianceArtifactPackage.dirLicensings.forEach { dirLicensing ->
            dirLicensing.licenses.forEach {
                if (it.licenseTextInArchive != null) {
                    filesToArchive.add(it.licenseTextInArchive!!)
                    it.licenseTextInArchive = "$prefix${it.licenseTextInArchive}"
                }
            }
        }
        complianceArtifactPackage.fileLicensings.forEach { fileLicensing ->
            if (fileLicensing.fileContentInArchive != null) {
                filesToArchive.add(fileLicensing.fileContentInArchive!!)
                fileLicensing.fileContentInArchive = "$prefix${fileLicensing.fileContentInArchive}"
            }
            fileLicensing.licenses.forEach {
                if (it.licenseTextInArchive != null) {
                    filesToArchive.add(it.licenseTextInArchive!!)
                    it.licenseTextInArchive = "$prefix${it.licenseTextInArchive}"
                }
            }
        }
    }

    /**
     * Generates a unique hash for a project
     */
    private fun getNewPrefix(project: Project): String {
        val key = project.complianceArtifactCollection.cid
        return DIGEST.digest(key.toByteArray()).joinToString("") {
             String.format(Locale.GERMAN, "%02x", it) } + "-"
    }

    /**
     * Packages with the same ID may only be different concerning the file references.
     */
    @Suppress("ComplexMethod")
    private fun inspectPackage(cap: Pack): Boolean {
        var error = false
        val oriCap = this.packs.firstOrNull { it.id == cap.id }!!

        // same version from different source repositories?
        error = error || oriCap.website != cap.website
        // find differences in defaultLicensings
        error = error || oriCap.defaultLicensings.size != cap.defaultLicensings.size
        oriCap.defaultLicensings.forEach { oriDefaultLicense ->
            error = error || cap.defaultLicensings.none {
                it.path == oriDefaultLicense.path && it.license == oriDefaultLicense.license }
        }
        // find differences in dirLicensings
        if (oriCap.dirLicensings.size != cap.dirLicensings.size) error = true
        oriCap.dirLicensings.forEach { oriDirLicensing ->
            error = error || (cap.dirLicensings.none { oriDirLicensing.scope == it.scope })
            val dirLicensing = cap.dirLicensings.firstOrNull { it.scope == oriDirLicensing.scope }
            if (dirLicensing == null) { error = true } else {
                error = error || (oriDirLicensing.licenses.size != dirLicensing.licenses.size)
                oriDirLicensing.licenses.forEach { oriDirLicense ->
                    error = error || (dirLicensing.licenses.none {
                        oriDirLicense.license == it.license && oriDirLicense.path == it.path })
                }
            }
        }
        // find differences in reuseLicensings
        error = error || oriCap.reuseLicensings.size != cap.reuseLicensings.size
        oriCap.reuseLicensings.forEach { oriReuseLicense ->
            error = error || cap.reuseLicensings.none { it.path == oriReuseLicense.path &&
                    it.license == oriReuseLicense.license }
        }
        // find differences in fileLicensings
        error = error || (oriCap.fileLicensings.size != cap.fileLicensings.size)
        oriCap.fileLicensings.forEach { oriFileLicensing ->
            error = error || (cap.fileLicensings.none { oriFileLicensing.scope == it.scope })
            val fileLicensing = cap.fileLicensings.firstOrNull { it.scope == oriFileLicensing.scope }
            if (fileLicensing == null) { error = true } else {
                error = error || (oriFileLicensing.licenses.size != fileLicensing.licenses.size)
                oriFileLicensing.licenses.forEach { oriFileLicense ->
                    error = error || (fileLicensing.licenses.none { oriFileLicense.license == it.license })
                }
                error = error || (oriFileLicensing.copyrights.size != fileLicensing.copyrights.size)
                oriFileLicensing.copyrights.forEach { oriFileCopyright ->
                    error = error || (fileLicensing.copyrights.none { oriFileCopyright.copyright == it.copyright })
                }
            }
        }

        if (error) logger.log("[${oriCap.origin}: ${cap.id}]: difference(s) in file ${cap.origin}!", Level.WARN,
            phase = ProcessingPhase.MERGING)
        return error
    }

    private fun containsID(id: Identifier): Boolean = this.packs.any { it.id == id }

    fun hideSections(sectionList: List<String>, tmpDirectory: File): Boolean {
        var haveHidden = false
        sectionList.forEach {
            when (it) {
                "config" -> {
                    this.config = null
                    haveHidden = true
                }
                "reuselicensings", "dirlicensings", "filelicensings" -> {
                    hideInPackages(it, tmpDirectory)
                    haveHidden = true
                }
            }
        }
        return haveHidden
    }

    private fun hideInPackages(section: String, tmpDirectory: File) {
        this.packs.forEach { pack ->
            when (section) {
                "reuselicensings" -> {
                    pack.reuseLicensings.forEach { reuseLicensing ->
                        pack.dedupRemoveFile(tmpDirectory, reuseLicensing.licenseTextInArchive)
                    }
                    pack.reuseLicensings.clear()
                }
                "dirlicensings" -> {
                    pack.dirLicensings.forEach { dirLicensing ->
                        dirLicensing.licenses.forEach { dirLicense ->
                            pack.dedupRemoveFile(tmpDirectory, dirLicense.licenseTextInArchive)
                        }
                    }
                    pack.dirLicensings.clear()
                }
                "filelicensings" -> {
                    pack.fileLicensings.forEach { fileLicensing ->
                        pack.dedupRemoveFile(tmpDirectory, fileLicensing.fileContentInArchive)
                        fileLicensing.licenses.forEach { fileLicense ->
                            pack.dedupRemoveFile(tmpDirectory, fileLicense.licenseTextInArchive)
                        }
                    }
                    pack.fileLicensings.clear()
                }
            }
        }
    }

    fun isProcessingAllowed(logger: OSCakeLogger, osccFile: File, authorList: List<String>): Boolean {
        if (authorList.contains(this.complianceArtifactCollection.author)) {
            logger.log("The file \"${osccFile.name}\" cannot be processed, because it was already processed" +
                    " in a former run! Check \"author\" in input file!", Level.ERROR, phase = ProcessingPhase.CURATION)
            exitProcess(10)
        }
        if (this.containsHiddenSections == true) {
            logger.log("The file \"${osccFile.name}\" cannot be processed, because some sections are missing!" +
                    " (maybe it was created with config option \"hideSections\")", Level.ERROR,
                phase = ProcessingPhase.CURATION)
            exitProcess(12)
        }
        return true
    }

    /**
     * the method sets all hasIssues properties in the project to false
     */
    fun resetIssues() {
        this.hasIssues = false
        this.packs.forEach { pack ->
            pack.hasIssues = false
            pack.defaultLicensings.forEach {
                it.hasIssues = false
            }
            pack.dirLicensings.forEach { dirLicensing ->
                dirLicensing.licenses.forEach {
                    it.hasIssues = false
                }
            }
        }
    }
}
