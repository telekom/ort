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

import java.util.SortedSet

/**
 * The class FileLicensing is a collection of [FileLicense] instances for the given path (stored in [scope])
 */
@JsonPropertyOrder("scope", "fileContentInArchive", "licenses")
data class FileLicensing(
    /**
     * [scope] contains the name of the file to which the licenses belong.
     */
    @get:JsonProperty("fileScope") val scope: String
) {
    /**
     * Represents the path to the file containing the license text in the archive.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL) var fileContentInArchive: String? = null
    /**
     * [licenses] keeps a list of all license findings for this file.
     */
    @JsonProperty("fileLicenses") val licenses = mutableListOf<FileLicense>()
    /**
     * [copyrights] keeps a list of all copyright statements for this file.
     */
    @JsonProperty("fileCopyrights") val copyrights = mutableListOf<FileCopyright>()

    fun coversAllLicenses(resolveLicenses: SortedSet<String>): Boolean =
        resolveLicenses == licenses.mapNotNull { it.license }.toSortedSet()

    fun handleCompoundLicense(newLicense: String): List<String> {
        val filesToDelete = mutableListOf<String>()
        fileContentInArchive?.let { filesToDelete.add(fileContentInArchive!!) }
        licenses.filter { it.licenseTextInArchive != null }.forEach { filesToDelete.add(it.licenseTextInArchive!!) }
        fileContentInArchive = null
        licenses.clear()
        licenses.add(FileLicense(newLicense))
        return filesToDelete
    }
}
