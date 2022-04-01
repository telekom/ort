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

@file:Suppress("TooManyFunctions")
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

import java.io.File
import java.util.SortedSet

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.reporter.reporters.evaluatedmodel.EvaluatedModel

/**
 * The class [Pack] holds license information found in license files. The [Identifier] stores the unique name of the
 * project or package (e.g. "Maven:de.tdosca.tc06:tdosca-tc06:1.0"). License information is split up into different
 * [ScopeLevel]s: [defaultLicensings], [dirLicensings], etc. If the sourcecode is not placed in the
 * root directory of the repository (e.g. git), then the property [packageRoot] shows the path to the root directory
  */
@JsonPropertyOrder("pid", "release", "repository", "id", "distribution", "packageType", "sourceRoot", "reuseCompliant",
    "hasIssues", "issues", "defaultLicenses", "defaultCopyrights", "unifiedCopyrights", "dirLicensings",
    "reuseLicenses", "fileLicensings")
@JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = IssuesFilterCustom::class)
data class Pack(
    /**
     * Unique identifier for the package.
     */
    @JsonProperty("id") val id: Identifier,
    /**
     * [website] contains the URL directing to the source code repository.
     */
    @get:JsonProperty("repository") val website: String,
    /**
     * The [packageRoot] is set to the folder where the source code can be found (empty string = default).
     */
    @get:JsonProperty("sourceRoot") val packageRoot: String = ""
) {
    /**
     * Package ID: e.g. "tdosca-tc06"  - part of the [id].
     */
    @JsonProperty("pid") val name = id.name
    /**
     * Namespace of the package: e.g. "de.tdosca.tc06" - part of the [id].
     */
    @JsonIgnore
    var namespace = id.namespace
    /**
     * version number of the package: e.g. "1.0" - part of the [id].
     */
    @JsonProperty("release") val version = id.version
    /**
     * [type] describes the package manager for the package: e.g. "Maven" - part of the [id].
     */
    @JsonIgnore
    var type = id.type
    /**
     * [declaredLicenses] contains a set of licenses identified by the ORT analyzer.
     */
    @JsonIgnore
    var declaredLicenses: SortedSet<String> = sortedSetOf<String>()
    /**
     * If the package is REUSE compliant, this flag is set to true.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) var reuseCompliant: Boolean = false
    /**
     * [hasIssues] shows that issues have happened during processing.
     */
    var hasIssues: Boolean = false
    /**
     * contains issues for the package level
     */
    @JsonProperty("issues") val issueList: IssueList = IssueList()
    /**
     *  [defaultLicensings] contains a list of [DefaultLicense]s  for non-REUSE compliant packages.
     */
    @JsonProperty("defaultLicenses") val defaultLicensings = mutableListOf<DefaultLicense>()
    /**
     *  [defaultCopyrights] contains a list of [DefaultDirCopyright]s  for non-REUSE compliant packages.
     */
    val defaultCopyrights = mutableListOf<DefaultDirCopyright>()
    /**
     *  [unifiedCopyrights] contains a list of all Copyrights - only for deduplication.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL) var unifiedCopyrights: List<String>? = null
    /**
     *  This list is only filled for REUSE-compliant packages and contains a list of [DefaultLicense]s.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonProperty("reuseLicenses")
    var reuseLicensings = mutableListOf<ReuseLicense>()
    /**
     *  [dirLicensings] contains a list of [DirLicensing]s for non-REUSE compliant packages.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY) var dirLicensings = mutableListOf<DirLicensing>()
    /**
     *  [fileLicensings] contains a list of [fileLicensings]s.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY) var fileLicensings = mutableListOf<FileLicensing>()
    /**
     * [origin] contains the name of the source file and is set during deserialization
     */
    @JsonIgnore var origin: String = ""
    /**
     * Defines the kind of [distribution]
     */
    var distribution: DistributionType? = null
    /**
     * Defines the kind of [packageType]
     */
    var packageType: PackageType? = null

    /**
     * Removes the file specified in [path] from the directory, if there is no reference to it anymore
     */
    fun dedupRemoveFile(tmpDirectory: File, path: String?) {
        if (path != null) {
            val file = tmpDirectory.resolve(path)
            if (findReferences(path) == 1 && file.exists()) file.delete()
        }
    }
    /**
     * Finds the amount of references for the file passed in [path]
     */
    private fun findReferences(path: String): Int {
        var cnt = 0
        cnt += defaultLicensings.count { it.licenseTextInArchive == path }
        dirLicensings.forEach { dirLicensing ->
            cnt += dirLicensing.licenses.count { it.licenseTextInArchive == path }
        }
        fileLicensings.forEach { fileLicensing ->
            if (fileLicensing.fileContentInArchive == path) cnt++
            cnt += fileLicensing.licenses.count { it.licenseTextInArchive == path }
        }
        cnt += reuseLicensings.count { it.licenseTextInArchive == path }
        return cnt
    }

    fun removeDirDefaultScopes() {
        this.defaultLicensings.clear()
        this.dirLicensings.forEach { it.licenses.clear() }
    }

    /**
     * The method generates the Default- and Dir- Scope entries based on FileLicensings
     */
    fun createDirDefaultScopes(
        logger: OSCakeLogger, params: OSCakeConfigParams, phase: ProcessingPhase,
        foundInFileScopeConfigured: Boolean = false) {

        fileLicensings.forEach { fileLicensing ->
            val scopeLevel = getScopeLevel(fileLicensing.scope, packageRoot, params)
            if (scopeLevel == ScopeLevel.DEFAULT) {
                fileLicensing.licenses.forEach { fileLicense ->
                    var fileLicensingScope = fileLicensing.scope
                    if (foundInFileScopeConfigured && (CompoundLicense(fileLicense.originalLicenses).isCompound ||
                        CompoundLicense(fileLicense.license).isCompound)) {
                        fileLicensingScope = FOUND_IN_FILE_SCOPE_CONFIGURED
                    }
                    if (defaultLicensings.none { it.license == fileLicense.license &&
                                it.path == fileLicensingScope })
                        defaultLicensings.add(DefaultLicense(fileLicense.license, fileLicensingScope,
                            fileLicense.licenseTextInArchive, false, originalLicenses = fileLicense.originalLicenses))
                    else {
                        val ll = if (isLikeNOASSERTION(fileLicense.license)) Level.INFO else Level.DEBUG
                        logger.log("DefaultScope: multiple equal licenses <${fileLicense.license}> in the same " +
                                "file found - ignored!", ll, id, phase = phase)
                    }
                }
            }
            if (scopeLevel == ScopeLevel.DIR) {
                val dirScope = getDirScopePath(this, fileLicensing.scope)
                var fibPathWithoutPackage = getPathWithoutPackageRoot(this, fileLicensing.scope)
                val dirLicensing = dirLicensings.firstOrNull { it.scope == dirScope } ?: DirLicensing(dirScope)
                    .apply { dirLicensings.add(this) }
                fileLicensing.licenses.forEach { fileLicense ->
                    if (foundInFileScopeConfigured && (CompoundLicense(fileLicense.originalLicenses).isCompound ||
                            CompoundLicense(fileLicense.license).isCompound))
                        fibPathWithoutPackage = FOUND_IN_FILE_SCOPE_CONFIGURED
                    if (dirLicensing.licenses.none { it.license == fileLicense.license &&
                                it.path == fibPathWithoutPackage })
                        dirLicensing.licenses.add(DirLicense(fileLicense.license!!, fileLicense.licenseTextInArchive,
                            fibPathWithoutPackage, originalLicenses = fileLicense.originalLicenses))
                    else {
                        val ll = if (isLikeNOASSERTION(fileLicense.license)) Level.INFO else Level.DEBUG
                        logger.log("DirScope: : multiple equal licenses <${fileLicense.license}> in the same " +
                                "file found - ignored!", ll, id, phase = phase)
                    }
                }
            }
        }
    }

    fun deduplicateFileLicenses(tmpDirectory: File, fileLicensingsList: List<FileLicensing>,
                                cfgPresFileScopes: Boolean = false, cfgCompOnlyDist: Boolean = true) {
        fileLicensingsList.forEach { fileLicensing ->
            if (licensesContainedInScope(getDirScopePath(this, fileLicensing.scope), fileLicensing,
                    cfgPresFileScopes, cfgCompOnlyDist)) {
                // remove files from archive
                fileLicensing.licenses.filter { it.licenseTextInArchive != null }.forEach {
                    dedupRemoveFile(tmpDirectory, it.licenseTextInArchive)
                }
                fileLicensing.licenses.clear()
            }
        }
    }

    /**
     * Checks if the [licenses] are contained as licenses in a higher scope
     */
    private fun licensesContainedInScope(path: String, fileLicensing: FileLicensing, cfgPresFileScopes: Boolean,
                                         cfgCompOnlyDist: Boolean): Boolean {
        val licensesList = fileLicensing.licenses.mapNotNull { it.license }.toList()
        val dirLicensing = bestMatchedDirLicensing(path)
        if (dirLicensing != null) {
            if (isEqual(dirLicensing.licenses.map { it.license }.toList(), licensesList, cfgCompOnlyDist) &&
                !licensesList.contains("NOASSERTION")) {
                if (dirLicensing.licenses.any { it.path == fileLicensing.scope } && cfgPresFileScopes) return false
                return true
            }
        } else {
            if (isEqual(defaultLicensings.mapNotNull { it.license }.toList(), licensesList, cfgCompOnlyDist) &&
                !licensesList.contains("NOASSERTION")) {
                if (defaultLicensings.any { it.path == fileLicensing.scope } && cfgPresFileScopes) return false
                return true
            }
        }
        return false
    }

    private inline fun <reified T> isEqual(first: List<T>, second: List<T>,
                                           compareOnlyDistinctLicensesCopyrights: Boolean): Boolean {
        var firstList = first
        var secondList = second

        if (compareOnlyDistinctLicensesCopyrights) {
            firstList = first.distinct().toList()
            secondList = second.distinct().toList()
        }
        if (firstList.size != secondList.size) return false
        return firstList.sortedBy { it.toString() }.toTypedArray() contentEquals secondList.sortedBy { it.toString() }
            .toTypedArray()
    }

    /**
     * Find the best matching [DirLicensing] depending on the hierarchy based on the [path]
     */
    private fun bestMatchedDirLicensing(path: String): DirLicensing? {
        val dirList = mutableListOf<Pair<DirLicensing, Int>>()
        if (path.isNotEmpty()) {
            dirLicensings.filter { path.startsWith(it.scope) }.forEach { dirLicensing ->
                dirList.add(Pair(dirLicensing, path.replaceFirst(dirLicensing.scope, "").length))
            }
            if (dirList.isNotEmpty()) {
                val score = dirList.minOf { it.second }
                val bestMatchedDirLicensing = dirList.first { it.second == score }
                return bestMatchedDirLicensing.first
            }
        }
        return null
    }

    fun deduplicateDirDirLicenses(tmpDirectory: File, resolveMode: Boolean = false,
                                  compareOnlyDistinctLicensesCopyrights: Boolean = true) {
        dirLicensings.forEach { dirLicensing ->
            if (resolveMode && dirLicensing.licenses.none { it.path == FOUND_IN_FILE_SCOPE_CONFIGURED })
                return
            val dirLicensesList = dirLicensing.licenses.map { it.license }
            getParentDirLicensing(dirLicensing)?.let { parentDirLicensing ->
                val parentDirLicensingList = parentDirLicensing.licenses.map { it.license }
                if (isEqual(dirLicensesList, parentDirLicensingList, compareOnlyDistinctLicensesCopyrights)) {
                    dirLicensing.licenses.forEach { dirLicense ->
                        dedupRemoveFile(tmpDirectory, dirLicense.licenseTextInArchive)
                    }
                    dirLicensing.licenses.clear()
                }
            }
        }
    }

    private fun getParentDirLicensing(dl: DirLicensing): DirLicensing? {
        val dirList = mutableListOf<Pair<DirLicensing, Int>>()
        dirLicensings.filter { dl.scope.startsWith(it.scope) && dl.scope != it.scope }.forEach { dirLicensing ->
            dirList.add(Pair(dirLicensing, dl.scope.replaceFirst(dirLicensing.scope, "").length))
        }
        if (dirList.isNotEmpty()) {
            val score = dirList.minOf { it.second }
            val bestMatchedDirLicensing = dirList.first { it.second == score }
            return bestMatchedDirLicensing.first
        }
        return null
    }

    fun deduplicateDirDefaultLicenses(tmpDirectory: File, resolveMode: Boolean = false,
                                      compareOnlyDistinctLicensesCopyrights: Boolean = true) {
        val defaultLicensesList = defaultLicensings.mapNotNull { it.license }
        dirLicensings.forEach { dirLicensing ->
            if (resolveMode && dirLicensing.licenses.none { it.path == FOUND_IN_FILE_SCOPE_CONFIGURED })
                return
            val dirLicensesList = dirLicensing.licenses.map { it.license }.toList()
            if (isEqual(defaultLicensesList, dirLicensesList, compareOnlyDistinctLicensesCopyrights) &&
                !dirLicensesList.contains("NOASSERTION")) {
                dirLicensing.licenses.forEach { dirLicense ->
                    dedupRemoveFile(tmpDirectory, dirLicense.licenseTextInArchive)
                }
                dirLicensing.licenses.clear()
            }
        }
    }

    fun deduplicateFileCopyrights(cfgPresFileScopes: Boolean, cfgCompOnlyDist: Boolean = true) =
        fileLicensings.filter { copyrightsContainedInScope(getDirScopePath(this, it.scope), it, cfgPresFileScopes,
            cfgCompOnlyDist) }.forEach { fileLicensing -> fileLicensing.copyrights.clear() }

    /**
     * Checks if the copyrights are contained as copyrights in a higher scope
     */
    private fun copyrightsContainedInScope(path: String, fileLicensing: FileLicensing, cfgPresFileScopes: Boolean,
                                           cfgCompOnlyDist: Boolean): Boolean {
        val copyrightsList = fileLicensing.copyrights.map { it.copyright }.toList()
        val dirLicensing = bestMatchedDirLicensing(path)
        if (dirLicensing != null) {
            if (isEqual(dirLicensing.copyrights.mapNotNull { it.copyright }.toList(),
                    copyrightsList, cfgCompOnlyDist)) {
                if (dirLicensing.copyrights.any { it.path == fileLicensing.scope } && cfgPresFileScopes) return false
                return true
            }
        } else {
            if (isEqual(defaultCopyrights.mapNotNull { it.copyright }.toList(), copyrightsList, cfgCompOnlyDist)) {
                if (defaultCopyrights.any { it.path == fileLicensing.scope } && cfgPresFileScopes) return false
                return true
            }
        }
        return false
    }

    fun deduplicateDirDirCopyrights(cfgCompOnlyDist: Boolean) {
        dirLicensings.forEach { dirLicensing ->
            val dirCopyrightsList = dirLicensing.copyrights.mapNotNull { it.copyright }
            getParentDirLicensing(dirLicensing)?.let { parentDirLicensing ->
                val parentDirCopyrightsLi = parentDirLicensing.copyrights.mapNotNull { it.copyright }
                if (isEqual(dirCopyrightsList, parentDirCopyrightsLi, cfgCompOnlyDist)) dirLicensing.copyrights.clear()
            }
        }
    }

    fun deduplicateDirDefaultCopyrights(cfgCompOnlyDist: Boolean) {
        val defaultCopyrightsList = defaultCopyrights.mapNotNull { it.copyright }
        dirLicensings.forEach { dirLicensing ->
            val dirCopyrightsList = dirLicensing.copyrights.mapNotNull { it.copyright }.toList()
            if (isEqual(defaultCopyrightsList, dirCopyrightsList, cfgCompOnlyDist)) dirLicensing.copyrights.clear()
        }
    }

    fun removeEmptyFileScopes(tmpDirectory: File) {
        val fileLicensings2Remove = fileLicensings.filter { it.licenses.isEmpty() && it.copyrights.isEmpty() }
        fileLicensings2Remove.forEach {
            dedupRemoveFile(tmpDirectory, it.fileContentInArchive)
        }
        fileLicensings.removeAll(fileLicensings2Remove)
    }

    fun removeEmptyDirScopes(tmpDirectory: File) {
        val dirLicensings2Remove = dirLicensings.filter { it.licenses.isEmpty() && it.copyrights.isEmpty() }
        dirLicensings2Remove.forEach { dirLicensing ->
            dirLicensing.licenses.forEach {
                dedupRemoveFile(tmpDirectory, it.licenseTextInArchive)
            }
        }
        dirLicensings.removeAll(dirLicensings2Remove.toSet())
    }

    fun removePackageIssues(): List<String> {
        val issuesNumbers = issueList.errors.map { it.id } + issueList.warnings.map { it.id }
        issueList.errors.clear()
        issueList.warnings.clear()
        issueList.infos.clear()
        return issuesNumbers
    }

    fun setDistributionType(evaluatedModel: EvaluatedModel, projectId: Identifier) {
        if (projectId == id)
            // the scope for a package which is the project is not set in evaluatedModel
            evaluatedModel.packages.firstOrNull { it.id == id && it.isProject }?.apply {
                distribution = distribution?.plus(getDistributionType(projectId, ""))
            }
        else
            evaluatedModel.packages.firstOrNull { it.id == id }?.scopes?.forEach {
                distribution = if (distribution == null)
                    getDistributionType(projectId, it.name)
                else
                    distribution?.plus(getDistributionType(projectId, it.name))
            }
        // default rule
        if (distribution == null) distribution = DistributionType.DISTRIBUTED
    }

    private fun getDistributionType(projectId: Identifier, scope: String): DistributionType? {
        // the projectId contains as type the used package manager (see definition in ORT)
        val packageManager = projectId.type
        var pmDist: String? = null
        var dist: String? = null

        OSCakeConfiguration.params.distributionMap.forEach { item ->
            val regex = item.key.toRegex()
            if (regex.containsMatchIn("$packageManager:$scope")) pmDist = item.value
            if (regex.containsMatchIn(scope)) dist = item.value
        }
        if (pmDist != null) return DistributionType.valueOf(pmDist!!)
        if (dist != null) return DistributionType.valueOf(dist!!)

        return null
    }
}
