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
package org.ossreviewtoolkit.oscake.deduplicator

import java.io.File

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*

/**
    The [PackDeduplicator] deduplicates file licenses and copyrights on all scopes for a specific [pack]age. The
    [tmpDirectory] holds a reference to the directory where the license files are stored (unzipped archive).
 */
class PackDeduplicator(private val pack: Pack, private val tmpDirectory: File,
                       private val config: OSCakeConfiguration) {

    fun deduplicate() {
        deduplicateFileLicenses()
        deduplicateFileCopyrights()
        deduplicateDirDirLicenses()
        deduplicateDirDirCopyrights()
        deduplicateDirDefaultLicenses()
        deduplicateDirDefaultCopyrights()

        // clean up empty entities
        val fileLicensings2Remove = mutableListOf<FileLicensing>()
        pack.fileLicensings.forEach { fileLicensing ->
            if (fileLicensing.licenses.isEmpty() && fileLicensing.copyrights.isEmpty() &&
                fileLicensing.fileContentInArchive == null) fileLicensings2Remove.add(fileLicensing)
        }
        pack.fileLicensings.removeAll(fileLicensings2Remove)
        pack.dirLicensings.removeAll(pack.dirLicensings.filter { it.licenses.isEmpty() && it.copyrights.isEmpty() })

        config.deduplicator?.keepEmptyScopes?.let {
            if (!it) {
                removeEmptyFileScopes()
                removeEmptyDirScopes()
            }
        }
    }

    private fun removeEmptyFileScopes() {
        val fileLicensings2Remove = pack.fileLicensings.filter { it.licenses.isEmpty() && it.copyrights.isEmpty() }
        fileLicensings2Remove.forEach {
            dedupRemoveFile(tmpDirectory, it.fileContentInArchive)
        }
        pack.fileLicensings.removeAll(fileLicensings2Remove)
    }

    private fun removeEmptyDirScopes() {
        val dirLicensings2Remove = pack.dirLicensings.filter { it.licenses.isEmpty() && it.copyrights.isEmpty() }
        dirLicensings2Remove.forEach { dirLicensing ->
            dirLicensing.licenses.forEach {
                dedupRemoveFile(tmpDirectory, it.licenseTextInArchive)
            }
        }
        pack.dirLicensings.removeAll(dirLicensings2Remove.toSet())
    }

    /**
     * Find the best matching [DirLicensing] depending on the hierarchy based on another [DirLicensing]
     */
    private fun getParentDirLicensing(dl: DirLicensing): DirLicensing? {
        val dirList = mutableListOf<Pair<DirLicensing, Int>>()
        pack.dirLicensings.filter { dl.scope.startsWith(it.scope) && dl.scope != it.scope }.forEach { dirLicensing ->
            dirList.add(Pair(dirLicensing, dl.scope.replaceFirst(dirLicensing.scope, "").length))
        }
        if (dirList.isNotEmpty()) {
            val score = dirList.minOf { it.second }
            val bestMatchedDirLicensing = dirList.first { it.second == score }
            return bestMatchedDirLicensing.first
        }
        return null
    }

    private fun deduplicateFileLicenses() {
        pack.fileLicensings.forEach { fileLicensing ->
            if (licensesContainedInScope(getDirScopePath(pack, fileLicensing.scope), fileLicensing)) {
                // remove files from archive
                fileLicensing.licenses.filter { it.licenseTextInArchive != null }.forEach {
                    dedupRemoveFile(tmpDirectory, it.licenseTextInArchive)
                }
                fileLicensing.licenses.clear()
            }
        }
    }

    private fun deduplicateFileCopyrights() =
        pack.fileLicensings.filter { copyrightsContainedInScope(getDirScopePath(pack, it.scope), it) }
            .forEach { fileLicensing -> fileLicensing.copyrights.clear()
            }

    private fun deduplicateDirDirLicenses() {
        pack.dirLicensings.forEach { dirLicensing ->
            val dirLicensesList = dirLicensing.licenses.map { it.license }
            getParentDirLicensing(dirLicensing)?.let { parentDirLicensing ->
                val parentDirLicensingList = parentDirLicensing.licenses.map { it.license }
                if (isEqual(dirLicensesList, parentDirLicensingList)) {
                    dirLicensing.licenses.forEach { dirLicense ->
                        dedupRemoveFile(tmpDirectory, dirLicense.licenseTextInArchive)
                    }
                    dirLicensing.licenses.clear()
                }
            }
        }
    }

    private fun deduplicateDirDirCopyrights() {
        pack.dirLicensings.forEach { dirLicensing ->
            val dirCopyrightsList = dirLicensing.copyrights.mapNotNull { it.copyright }
            getParentDirLicensing(dirLicensing)?.let { parentDirLicensing ->
                val parentDirCopyrightsList = parentDirLicensing.copyrights.mapNotNull { it.copyright }
                if (isEqual(dirCopyrightsList, parentDirCopyrightsList)) dirLicensing.copyrights.clear()
            }
        }
    }

    private fun deduplicateDirDefaultLicenses() {
        val defaultLicensesList = pack.defaultLicensings.mapNotNull { it.license }
        pack.dirLicensings.forEach { dirLicensing ->
            val dirLicensesList = dirLicensing.licenses.mapNotNull { it.license }.toList()
            if (isEqual(defaultLicensesList, dirLicensesList) && !dirLicensesList.contains("NOASSERTION")) {
                dirLicensing.licenses.forEach { dirLicense ->
                    dedupRemoveFile(tmpDirectory, dirLicense.licenseTextInArchive)
                }
                dirLicensing.licenses.clear()
            }
        }
    }

    private fun deduplicateDirDefaultCopyrights() {
        val defaultCopyrightsList = pack.defaultCopyrights.mapNotNull { it.copyright }
        pack.dirLicensings.forEach { dirLicensing ->
            val dirCopyrightsList = dirLicensing.copyrights.mapNotNull { it.copyright }.toList()
            if (isEqual(defaultCopyrightsList, dirCopyrightsList)) dirLicensing.copyrights.clear()
        }
    }

    /**
     * Find the best matching [DirLicensing] depending on the hierarchy based on the [path]
     */
    private fun bestMatchedDirLicensing(path: String): DirLicensing? {
        val dirList = mutableListOf<Pair<DirLicensing, Int>>()
        if (path.isNotEmpty()) {
            pack.dirLicensings.filter { path.startsWith(it.scope) }.forEach { dirLicensing ->
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

    /**
     * Checks if the [licenses] are contained as licenses in a higher scope
     */
    private fun licensesContainedInScope(path: String, fileLicensing: FileLicensing): Boolean {
        val cfgPreserveFileScopes = config.deduplicator?.preserveFileScopes == true
        val licensesList = fileLicensing.licenses.mapNotNull { it.license }.toList()
        val dirLicensing = bestMatchedDirLicensing(path)
        if (dirLicensing != null) {
            if (dirPreservedByConfig(dirLicensing.scope, fileLicensing)) return false
            if (!cfgPreserveFileScopes && dirLicensing.licenses.any { it.path == fileLicensing.scope }) return true
            if (isEqual(dirLicensing.licenses.mapNotNull { it.license }.toList(), licensesList) &&
                !licensesList.contains("NOASSERTION")) return true
        } else {
            if (defaultPreservedByConfig(fileLicensing)) return false
            if (pack.defaultLicensings.any { it.path == fileLicensing.scope }) return !cfgPreserveFileScopes
            if (isEqual(pack.defaultLicensings.mapNotNull { it.license }.toList(), licensesList) &&
                !licensesList.contains("NOASSERTION")) return true
        }
        return false
    }

    /**
     * Checks if the [copyrights] are contained as copyrights in a higher scope
     */
    private fun copyrightsContainedInScope(path: String, fileLicensing: FileLicensing): Boolean {
        val cfgPreserveFileScopes = config.deduplicator?.preserveFileScopes == true
        val copyrightsList = fileLicensing.copyrights.mapNotNull { it.copyright }.toList()
        val dirLicensing = bestMatchedDirLicensing(path)
        if (dirLicensing != null) {
            if (dirPreservedByConfig(dirLicensing.scope, fileLicensing)) return false
            if (!cfgPreserveFileScopes && dirLicensing.copyrights.any { it.path == fileLicensing.scope }) return true
            if (isEqual(dirLicensing.copyrights.mapNotNull { it.copyright }.toList(), copyrightsList)) return true
        } else {
            if (defaultPreservedByConfig(fileLicensing)) return false
            if (pack.defaultCopyrights.any { it.path == fileLicensing.scope }) return !cfgPreserveFileScopes
            if (isEqual(pack.defaultCopyrights.mapNotNull { it.copyright }.toList(), copyrightsList)) return true
        }
        return false
    }

    private fun dirPreservedByConfig(parentScope: String, fileLicensing: FileLicensing) = (config.deduplicator?.
        preserveFileScopes == true && parentScope == fileLicensing.scope)

    private fun defaultPreservedByConfig(fileLicensing: FileLicensing) = (config.deduplicator?.
        preserveFileScopes == true && pack.defaultLicensings.any { it.path == fileLicensing.scope })

    /**
     * Compares two lists for equality
     */
    private inline fun <reified T> isEqual(first: List<T>, second: List<T>): Boolean {
        var firstList = first
        var secondList = second

        if (config.deduplicator?.compareOnlyDistinctLicensesCopyrights == true) {
            firstList = first.distinct().toList()
            secondList = second.distinct().toList()
        }
        if (firstList.size != secondList.size) return false
        return firstList.sortedBy { it.toString() }.toTypedArray() contentEquals secondList.sortedBy { it.toString() }
            .toTypedArray()
    }

    /**
     * Removes the file specified in [path] from the directory, if there is no reference to it anymore
     */
    private fun dedupRemoveFile(tmpDirectory: File, path: String?) {
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
        cnt += pack.defaultLicensings.count { it.licenseTextInArchive == path }
        pack.dirLicensings.forEach { dirLicensing ->
            cnt += dirLicensing.licenses.count { it.licenseTextInArchive == path }
        }
        pack.fileLicensings.forEach { fileLicensing ->
            if (fileLicensing.fileContentInArchive == path) cnt++
            cnt += fileLicensing.licenses.count { it.licenseTextInArchive == path }
        }
        return cnt
    }
}
