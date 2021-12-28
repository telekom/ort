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

import java.io.File
import java.io.FileOutputStream
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
@JsonPropertyOrder("hasIssues", "issues", "config", "complianceArtifactCollection", "complianceArtifactPackages")
@JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = IssuesFilterCustom::class)
data class Project(
    /**
     * [hasIssues] shows if problems occurred during processing the data.
     */
    var hasIssues: Boolean = false,
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
    @get:JsonProperty("complianceArtifactPackages") val packs: MutableList<Pack> = mutableListOf<Pack>()
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
    }

    // shows that this project is the target project
    private var isInitialProject = false

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

        val packagesToAdd = mutableListOf<Pack>()
        val filesToArchive = mutableListOf<String>()
        val prefix = getNewPrefix(project)
        var absoluteFilePathToZip: File?
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
        } finally {
            return rc
        }
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

    internal fun hideSections(sectionList: List<String>) {
        sectionList.forEach {
            when (it) {
                "config" -> this.config = null
                "reuselicensings", "dirlicensings", "filelicensings" -> hideInPackages(it)
            }
        }
    }

    private fun hideInPackages(section: String) {
        this.packs.forEach {
            when (section) {
                "reuselicensings" -> it.reuseLicensings.clear()
                "dirlicensings" -> it.dirLicensings.clear()
                "filelicensings" -> it.fileLicensings.clear()
            }
        }
    }

}
