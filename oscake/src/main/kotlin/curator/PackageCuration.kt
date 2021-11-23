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

package org.ossreviewtoolkit.oscake.curator

import com.fasterxml.jackson.annotation.JsonProperty

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException

import java.io.File
import java.nio.file.FileSystems

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.oscake.CURATION_DEFAULT_LICENSING
import org.ossreviewtoolkit.oscake.CURATION_LOGGER
import org.ossreviewtoolkit.oscake.orderCopyrightByModifier
import org.ossreviewtoolkit.oscake.orderLicenseByModifier
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*

/**
 * A [PackageCuration] contains a curation for a specific package, identified by an [id]. The instances are created
 * during processing the curation (yml) files.
 */
internal data class PackageCuration(
    /**
     * The [id] contains a package identification as defined in the [Identifier] class. The version information may
     * be stored as a specific version number, an IVY-expression (describing a range of versions) or may be empty (then
     * it will be valid for all version numbers).
     */
    val id: Identifier,
    /**
     * The [packageModifier] indicates that a package should be inserted, updated or deleted.
     */
    @JsonProperty("package_modifier") val packageModifier: String,
    /**
     * List of issue ids which are treated by the curation e.g. "E01", "W02", etc.
     * only allowed for packageModifier != "insert"
     */
    @JsonProperty("resolved_issues") val resolvedIssues: List<String>? = mutableListOf(),
    /**
     * An optional [comment] discusses the reason for this curation.
     */
    val comment: String? = null,
    /**
     * Only for [packageModifier] == "insert" the [repository] is necessary.
     */
    val repository: String? = null,
    /**
     * List of [CurationFileItem]s to be applied for this [id].
     */
    val curations: List<CurationFileItem>?
) {
    /**
     * The [logger] is only initialized, if there is something to log.
     */
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(CURATION_LOGGER) }

    /**
     * Returns true if this [PackageCuration] is applicable to the package with the given [pkgId],
     * disregarding the version.
     */
    private fun isApplicableDisregardingVersion(pkgId: Identifier) =
        id.type.equals(pkgId.type, ignoreCase = true)
                && id.namespace == pkgId.namespace
                && id.name == pkgId.name

    /**
     * Returns true if the version of this [PackageCuration] interpreted as an Ivy version matcher is applicable to the
     * package with the given [pkgId].
     */
     private fun isApplicableIvyVersion(pkgId: Identifier) =
        @Suppress("SwallowedException")
         try {
            val pkgIvyVersion = Semver(pkgId.version, Semver.SemverType.IVY)
            pkgIvyVersion.satisfies(id.version)
        } catch (e: SemverException) {
            false
        }

    /**
     * Returns true if this string equals the [other] string, or if either string is blank.
     */
    private fun String.equalsOrIsBlank(other: String) = equals(other) || isBlank() || other.isBlank()

    /**
     * Return true if this [PackageCuration] is applicable to the package with the given [pkgId]. The
     * curation's version may be an
     * [Ivy version matcher](http://ant.apache.org/ivy/history/2.4.0/settings/version-matchers.html).
     */
    fun isApplicable(pkgId: Identifier): Boolean =
        isApplicableDisregardingVersion(pkgId)
                && (id.version.equalsOrIsBlank(pkgId.version) || isApplicableIvyVersion(pkgId))

    /**
     * The method handles curations for the [packageModifier]s "insert" and "update". Additionally, it manages
     * the special case of [fileScope] == CURATION_DEFAULT_LICENSING (This special processing is necessary if
     * defaultLicensings exist, which are not based on fileLicensings - aka "declared license").
     */
    internal fun curate(pack: Pack, archiveDir: File, fileStore: File, scopePatterns: List<String>) {
        if (packageModifier == "insert" || packageModifier == "update") {
            eliminateIssueFromPackage(pack)
            curations?.filter { it.fileScope != CURATION_DEFAULT_LICENSING }?.forEach { curationFileItem ->
                val fileScope = getPathWithoutPackageRoot(pack, curationFileItem.fileScope)
                curationFileItem.fileLicenses?.sortedBy { orderLicenseByModifier[packageModifier]?.get(it.modifier)
                    }?.forEach { curationFileLicenseItem ->
                    when (curationFileLicenseItem.modifier) {
                        "insert" -> curateLicenseInsert(
                            curationFileItem, curationFileLicenseItem, pack, archiveDir,
                            fileStore, scopePatterns
                        )
                        "delete" -> curateLicenseDelete(curationFileItem, curationFileLicenseItem, pack, archiveDir)
                        "update" -> curateLicenseUpdate(
                            curationFileItem, curationFileLicenseItem, pack, archiveDir,
                            fileStore
                        )
                    }
                }
                curationFileItem.fileCopyrights?.sortedBy { orderCopyrightByModifier[packageModifier]?.get(it.modifier)
                    }?.forEach { curationFileCopyrightItem ->
                    when (curationFileCopyrightItem.modifier) {
                        "insert" -> curateCopyrightInsert(fileScope, curationFileCopyrightItem, pack)
                        "delete" -> curateCopyrightDelete(fileScope, pack, curationFileCopyrightItem.copyright!!)
                        "delete-all" -> curateCopyrightDeleteAll(fileScope, pack)
                    }
                }
            }
            curations?.filter { it.fileScope == CURATION_DEFAULT_LICENSING }?.forEach { curationFileItem ->
                curationFileItem.fileLicenses?.sortedBy { orderLicenseByModifier[packageModifier]?.get(it.modifier)
                    }?.forEach { curationFileLicenseItem ->
                    when (curationFileLicenseItem.modifier) {
                        "insert" -> pack.defaultLicensings.add(
                            DefaultLicense(
                                curationFileLicenseItem.license,
                                FOUND_IN_FILE_SCOPE_DECLARED, insertLTIA(
                                    curationFileLicenseItem.licenseTextInArchive,
                                    archiveDir, curationFileLicenseItem.license!!, fileStore, pack
                                ), true
                            )
                        )
                        "delete" -> pack.defaultLicensings.filter {
                            it.declared && (it.license == curationFileLicenseItem.license ||
                                    curationFileLicenseItem.license == "*") &&
                                    (it.licenseTextInArchive == curationFileLicenseItem.licenseTextInArchive ||
                                            curationFileLicenseItem.licenseTextInArchive == "*")
                        }.also {
                            it.forEach { deleteFromArchive(it.licenseTextInArchive, archiveDir) }
                        }.also {
                            pack.defaultLicensings.removeAll(it)
                        }
                        "update" -> pack.defaultLicensings.filter {
                            it.declared && (it.license == curationFileLicenseItem.license ||
                                    curationFileLicenseItem.license == "*") &&
                                    it.licenseTextInArchive != curationFileLicenseItem.licenseTextInArchive
                        }.forEach {
                            deleteFromArchive(it.licenseTextInArchive, archiveDir)
                            it.licenseTextInArchive = curationFileLicenseItem.licenseTextInArchive
                            if (curationFileLicenseItem.licenseTextInArchive != null) {
                                it.licenseTextInArchive = insertLTIA(
                                    curationFileLicenseItem.licenseTextInArchive,
                                    archiveDir, it.license ?: "", fileStore, pack
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun curateLicenseUpdate(
        curationFileItem: CurationFileItem,
        curationFileLicenseItem: CurationFileLicenseItem,
        pack: Pack,
        archiveDir: File,
        fileStore: File
    ) {
        val fileScope = getPathWithoutPackageRoot(pack, curationFileItem.fileScope)
        val fileSystem = FileSystems.getDefault()

        pack.fileLicensings.filter { fileSystem.getPathMatcher("glob:$fileScope").matches(
            File(it.scope).toPath()
        ) }.forEach { fileLicensing ->
            fileLicensing.licenses.filter {
                curationFileLicenseItem.license == "*" || curationFileLicenseItem.license == it.license
                }.forEach { fileLicense ->
                    var hasChanged = false
                    if (fileLicense.licenseTextInArchive != null &&
                        curationFileLicenseItem.licenseTextInArchive == null) {
                        hasChanged = true
                        deleteFromArchive(fileLicense.licenseTextInArchive, archiveDir)
                        fileLicense.licenseTextInArchive = null
                    }
                    if (curationFileLicenseItem.licenseTextInArchive != "*" &&
                        curationFileLicenseItem.licenseTextInArchive != null) {

                        if (fileLicense.licenseTextInArchive != null &&
                            fileLicense.licenseTextInArchive != curationFileLicenseItem.licenseTextInArchive
                        ) {
                            hasChanged = true
                            deleteFromArchive(fileLicense.licenseTextInArchive, archiveDir)
                            fileLicense.licenseTextInArchive = insertLTIA(
                                curationFileLicenseItem.licenseTextInArchive,
                                archiveDir,
                                fileLicense.license ?: "null",
                                fileStore,
                                pack
                            )
                        }
                        if (fileLicense.licenseTextInArchive == null) {
                            hasChanged = true
                            fileLicense.licenseTextInArchive = insertLTIA(
                                curationFileLicenseItem.licenseTextInArchive,
                                archiveDir,
                                fileLicense.license ?: "null",
                                fileStore,
                                pack
                            )
                        }
                    }
                    if (hasChanged) {
                        // update defaultScope
                        pack.defaultLicensings.filter { it.path == fileLicensing.scope &&
                                it.license == fileLicense.license }.forEach {
                                    it.licenseTextInArchive = fileLicense.licenseTextInArchive
                        }
                        // dirScope
                        pack.dirLicensings.forEach { dirLicensing ->
                            dirLicensing.licenses.filter { it.path == fileLicensing.scope &&
                                it.license == fileLicense.license }.forEach {
                                    it.licenseTextInArchive = fileLicense.licenseTextInArchive
                            }
                        }
                        // update reuseScope
                        pack.reuseLicensings.filter { it.path == fileLicensing.scope &&
                                it.license == fileLicense.license }.forEach {
                            it.licenseTextInArchive = fileLicense.licenseTextInArchive
                        }
                    }
            }
        }
    }

    private fun curateLicenseDelete(
        curationFileItem: CurationFileItem,
        curationFileLicenseItem: CurationFileLicenseItem,
        pack: Pack,
        archiveDir: File
    ) {
        val fileLicensingsToDelete = mutableListOf<FileLicensing>()
        val fileScope = getPathWithoutPackageRoot(pack, curationFileItem.fileScope)
        val fileSystem = FileSystems.getDefault()

        pack.fileLicensings.filter { fileSystem.getPathMatcher("glob:$fileScope").matches(
            File(it.scope).toPath()
        ) }.forEach { fileLicensing ->

            val itemsClone = mutableListOf<FileLicense>().apply { addAll(fileLicensing.licenses) }
            fileLicensing.licenses.clear()
            itemsClone.forEach { licenseEntry ->
                var copyCandidate = true
                if (curationFileLicenseItem.license == "*") {
                    if (curationFileLicenseItem.licenseTextInArchive == licenseEntry.licenseTextInArchive ||
                        curationFileLicenseItem.licenseTextInArchive == "*") copyCandidate = false
                } else {
                    if (curationFileLicenseItem.license == licenseEntry.license &&
                        (curationFileLicenseItem.licenseTextInArchive == licenseEntry.licenseTextInArchive ||
                                curationFileLicenseItem.licenseTextInArchive == "*")) copyCandidate = false
                }
                if (copyCandidate) fileLicensing.licenses.add(licenseEntry)
                else {
                    // delete from defaultScope
                    pack.defaultLicensings.removeAll(pack.defaultLicensings.filter {
                        it.path == fileLicensing.scope && it.license == licenseEntry.license &&
                                it.licenseTextInArchive == licenseEntry.licenseTextInArchive
                    })
                    // delete from reuseLicensings
                    pack.reuseLicensings.removeAll(pack.reuseLicensings.filter {
                        it.path == fileLicensing.scope && it.license == licenseEntry.license &&
                                it.licenseTextInArchive == licenseEntry.licenseTextInArchive
                    })
                    deleteFromArchive(licenseEntry.licenseTextInArchive, archiveDir)

                    // delete from DirScope
                    pack.dirLicensings.forEach { dirLicensing ->
                        dirLicensing.licenses.removeAll(dirLicensing.licenses.filter {
                            it.path == fileLicensing.scope && it.license == licenseEntry.license &&
                                    it.licenseTextInArchive == licenseEntry.licenseTextInArchive
                        })
                    }
                    // clean up DirScope: there may be a dirScope without any license
                    pack.dirLicensings.removeAll(pack.dirLicensings.filter { it.licenses.size == 0 })
                }
            }
            // clean-up FileLicensing
            if (fileLicensing.licenses.size == 0) {
                if (fileLicensing.copyrights.size == 0) fileLicensingsToDelete.add(fileLicensing)
                deleteFromArchive(fileLicensing.fileContentInArchive, archiveDir)
                fileLicensing.fileContentInArchive = null
            }
        }
        pack.fileLicensings.removeAll(fileLicensingsToDelete)
    }

    private fun curateCopyrightInsert(fileScope: String, curFileCopyrightItem: CurationFileCopyrightItem, pack: Pack) =
        (pack.fileLicensings.firstOrNull { it.scope == fileScope } ?: (FileLicensing(fileScope).apply {
            pack.fileLicensings.add(this) })
                ).apply {
                    copyrights.add(
                        FileCopyright(curFileCopyrightItem.copyright!!.replace("\\?", "?"
                        ).replace("\\*", "*"))
                    )
                }

    private fun curateCopyrightDeleteAll(fileScope: String, pack: Pack) {
        val fileLicensingsToDelete = mutableListOf<FileLicensing>()
        val fileSystem = FileSystems.getDefault()
        pack.fileLicensings.filter { fileSystem.getPathMatcher("glob:$fileScope").matches(
            File(it.scope).toPath()
        ) }.forEach { fileLicensing ->
                fileLicensing.copyrights.clear()
                if (fileLicensing.licenses.size == 0 && fileLicensing.copyrights.size == 0) {
                    fileLicensingsToDelete.add(fileLicensing)
                }
        }
        pack.fileLicensings.removeAll(fileLicensingsToDelete)
    }

    private fun curateCopyrightDelete(fileScope: String, pack: Pack, copyrightMatch: String) {
        val fileLicensingsToDelete = mutableListOf<FileLicensing>()
        val fileSystem = FileSystems.getDefault()
        pack.fileLicensings.filter { fileSystem.getPathMatcher("glob:$fileScope").matches(
            File(it.scope).toPath()
        ) }.forEach { fileLicensing ->
            val copyrightsToDelete = mutableListOf<FileCopyright>()
            fileLicensing.copyrights.forEach {
                // simple pattern matching
                // replace escaped characters with arbitrary character
                if (match(copyrightMatch.replace("\\*", "Ä").replace("\\?", "Ö"),
                        it.copyright.replace("*", "Ä").replace("?", "Ö"))) {
                    copyrightsToDelete.add(it)
                }
            }
            fileLicensing.copyrights.removeAll(copyrightsToDelete)
            if (fileLicensing.licenses.size == 0 && fileLicensing.copyrights.size == 0) {
                fileLicensingsToDelete.add(fileLicensing)
            }
        }
        pack.fileLicensings.removeAll(fileLicensingsToDelete)
    }

    private fun match(wildCardString: String, compareString: String): Boolean {
        // two consecutive "*" are not allowed
        if (wildCardString.contains("**")) return false

        if (wildCardString.length == 0 && compareString.length == 0) return true

        if (wildCardString.length > 1 && wildCardString[0] == '*' && compareString.length == 0) return false

        @Suppress("ComplexCondition")
        if (wildCardString.length > 1 && wildCardString[0] == '?' ||
            wildCardString.length != 0 && compareString.length != 0 && wildCardString[0] == compareString[0]
        ) return match(
            wildCardString.substring(1),
            compareString.substring(1)
        )

        return if (wildCardString.length > 0 && wildCardString[0] == '*') match(wildCardString.substring(1),
            compareString) || match(wildCardString, compareString.substring(1)) else false
    }

    private fun curateLicenseInsert(
        curationFileItem: CurationFileItem,
        curationFileLicenseItem: CurationFileLicenseItem,
        pack: Pack,
        archiveDir: File,
        fileStore: File,
        scopePatterns: List<String>
    ) {
        val scopeLevel = getScopeLevel(curationFileItem.fileScope,
            pack.packageRoot, scopePatterns)
        val fileScope = getPathWithoutPackageRoot(pack, curationFileItem.fileScope)
        val fileLicense: FileLicense
        (pack.fileLicensings.firstOrNull { it.scope == fileScope } ?: FileLicensing(fileScope)).apply {
            if (pack.reuseCompliant && !licenses.isEmpty()) {
                logger.log("In REUSE compliant projects only one license per is file allowed --> curation " +
                        "insert ignored!", Level.WARN, pack.id, curationFileItem.fileScope)
                return
            }
            fileLicense = FileLicense(
                curationFileLicenseItem.license,
                insertLTIA(
                    curationFileLicenseItem.licenseTextInArchive,
                    archiveDir,
                    curationFileLicenseItem.license!!,
                    fileStore,
                    pack
                )
            )
            licenses.add(fileLicense)
            if (pack.fileLicensings.none { it.scope == fileScope }) pack.fileLicensings.add(this)
        }

        if (pack.reuseCompliant && fileScope.startsWith(REUSE_LICENSES_FOLDER)) pack.reuseLicensings.add(
            ReuseLicense(
            fileLicense.license, fileScope, fileLicense.licenseTextInArchive)
        )

        if (!pack.reuseCompliant && scopeLevel == ScopeLevel.DEFAULT) {
            pack.defaultLicensings.add(
                DefaultLicense(
                    fileLicense.license, fileScope, fileLicense.licenseTextInArchive,
                    false
                )
            )
        }
        if (!pack.reuseCompliant && scopeLevel == ScopeLevel.DIR) {
            val dirScope = getDirScopePath(pack, fileScope)
            (pack.dirLicensings.firstOrNull() { it.scope == dirScope } ?: run
                {
                    DirLicensing(dirScope).apply { pack.dirLicensings.add(this) }
                }).apply {
                    licenses.add(DirLicense(fileLicense.license!!, fileLicense.licenseTextInArchive, fileScope))
                }
        }
    }

    /**
     * [insertLTIA] = insertLicenseTextInArchive: copies the file from the filestore to the archive and
     * checks for filename duplicates.
     */
    private fun insertLTIA(newPath: String?, archiveDir: File, license: String, fileStore: File, pack: Pack): String? {
        if (newPath == null) return null
        val targetFileName = createPathFlat(pack.id, newPath, license)
        val targetFileDeDup = File(deduplicateFileName(archiveDir.path + "/" + targetFileName))

        File(fileStore.path + "/" + newPath).apply {
            if (exists()) copyTo(targetFileDeDup)
            else {
                logger.log("File '$name' in file store not found --> set to null", Level.ERROR, pack.id)
                return null
            }
        }
        return targetFileDeDup.name
    }

    private fun eliminateIssueFromPackage(pack: Pack) {
        // generate list of all reported oscc-issues (ERRORs and WARNINGs) in this package
        val issueList2resolve = mutableListOf<String>()
        issueList2resolve.addAll(pack.issueList.warnings.map { it.id })
        issueList2resolve.addAll(pack.issueList.errors.map { it.id })
        issueList2resolve.addAll(pack.defaultLicensings.map { it.issueList.warnings.map { it.id } }.flatten())
        issueList2resolve.addAll(pack.defaultLicensings.map { it.issueList.errors.map { it.id } }.flatten())
        pack.dirLicensings.forEach {
            it.licenses.forEach {
                issueList2resolve.addAll(it.issueList.warnings.map { it.id })
                issueList2resolve.addAll(it.issueList.errors.map { it.id })
            }
        }
        // issues in curation for this package
        val remedyList = (resolvedIssues?.filter { it [0] == 'E' || it[0] == 'W' } ?: emptyList<String>())
            .toMutableList()
        val issueList2resolveClone = issueList2resolve.toList()
        val remedyListClone = remedyList.toList()
        issueList2resolve.removeAll(remedyList)
        if (issueList2resolve.isNotEmpty()) logger.log("Not every issue is resolved from package, the following" +
                " issues still remain: $issueList2resolve", Level.WARN, pack.id, phase = ProcessingPhase.CURATION)
        remedyList.removeAll(issueList2resolveClone)
        if (remedyList.isNotEmpty()) logger.log("Curation treats more issues, than the package really has: " +
                    " $remedyList", Level.WARN, pack.id, phase = ProcessingPhase.CURATION)

        // remove curated issues
        pack.issueList.infos.removeAll { remedyListClone.contains(it.id) }
        pack.issueList.warnings.removeAll { remedyListClone.contains(it.id) }
        pack.issueList.errors.removeAll { remedyListClone.contains(it.id) }

        pack.defaultLicensings.forEach {
            it.issueList.infos.removeAll { remedyListClone.contains(it.id) }
            it.issueList.warnings.removeAll { remedyListClone.contains(it.id) }
            it.issueList.errors.removeAll { remedyListClone.contains(it.id) }
        }

        pack.dirLicensings.forEach {
            it.licenses.forEach {
                it.issueList.infos.removeAll { remedyListClone.contains(it.id) }
                it.issueList.warnings.removeAll { remedyListClone.contains(it.id) }
                it.issueList.errors.removeAll { remedyListClone.contains(it.id) }
            }
        }
    }
}
