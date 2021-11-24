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

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.oscake.CURATION_LOGGER
import org.ossreviewtoolkit.oscake.packageModifierMap
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*

/**
 * The [CurationProvider] gets the locations where to find
 * - the yml-files containing curations (their semantics is checked while processing) and
 * - the corresponding license text files
 * and creates a list of possible [PackageCuration]s.
 */
internal class CurationProvider(
    /**
     * Contains the root folder containing curation files in yml format (may be organized in subdirectories).
     */
    curationDirectory: File,
    /**
     *  Contains the root folder containing files with license texts (may be organized in subdirectories).
     */
    fileStore: File
) {
    /**
     * List of available [PackageCuration]s
     */
    internal val packageCurations = mutableListOf<PackageCuration>()

    /**
     * The [logger] is only initialized, if there is something to log.
     */
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(CURATION_LOGGER) }

    /**
     * The init method walks through the folder hierarchy - starting at "curationDirectory" - and creates a list
     * of "PackageCurations".
     */
    init {
        if (curationDirectory.isDirectory) {
            curationDirectory.walkTopDown().filter { it.isFile && it.extension == "yml" }.forEach {
                try {
                    it.readValue<List<PackageCuration>>().forEach { packageCuration ->
                        if (checkSemantics(packageCuration, it.name, fileStore)) packageCurations.add(packageCuration)
                    }
                } catch (e: IOException) {
                    logger.log("Error while processing file: ${it.absoluteFile}! - Curation not applied! ${e.message}",
                        Level.ERROR, phase = ProcessingPhase.CURATION
                    )
                }
            }
        }
    }

    /**
     * Returns the [PackageCuration] which is applicable for a specific package Id or null if there is none or
     * more than one.
     */
    internal fun getCurationFor(pkgId: Identifier): PackageCuration? {
        packageCurations.filter { it.isApplicable(pkgId) }.apply {
            if (size > 1) logger.log("Error: more than one curation was found for" +
                    " package: $pkgId - don't know which one to take!", Level.ERROR, pkgId,
                phase = ProcessingPhase.CURATION
            )
            if (size != 1) return null
            return first()
        }
    }

    /**
     * Checks semantics of the [packageCuration]. In case of incongruities, these are logged and the curation is
     * not applied.
     */
    @Suppress("ComplexMethod", "LongMethod")
    private fun checkSemantics(packageCuration: PackageCuration, fileName: String, fileStore: File): Boolean {
        val errorPrefix = "[Semantics] - File: $fileName [${packageCuration.id.toCoordinates()}]: "
        val errorSuffix = " --> curation ignored"

        // 1. check Package-ID
        if (!packageIdIsValid(packageCuration.id)) {
            logger.log("$errorPrefix package <id> is not valid! $errorSuffix", Level.WARN,
                phase = ProcessingPhase.CURATION
            )
            return false
        }
        // 2. check packageModifier
        if (!packageModifierMap.containsKey(packageCuration.packageModifier)) {
            logger.log("$errorPrefix package_modifier <${packageCuration.packageModifier}> not valid! " +
                    errorSuffix, Level.WARN, phase = ProcessingPhase.CURATION
            )
            return false
        }
        // 3. no curations allowed for packageModifier: delete
        if (packageCuration.packageModifier == "delete" && !packageCuration.curations.isNullOrEmpty()) {
            logger.log("$errorPrefix if package_modifier = \"delete\" no curations are allowed! $errorSuffix",
                Level.WARN, phase = ProcessingPhase.CURATION
            )
            return false
        }
        // 4. repository must be defined for packageModifier: insert
        if (packageCuration.packageModifier == "insert" && packageCuration.repository == null) {
            logger.log("$errorPrefix if package_modifier = \"insert\" repository must be defined! $errorSuffix",
                Level.WARN, phase = ProcessingPhase.CURATION
            )
            return false
        }
        // 5. check modifiers in every licensing
        val modifiers = packageModifierMap.getOrElse(packageCuration.packageModifier) { listOf(setOf(), setOf()) }
        packageCuration.curations?.forEach { curationFileItem ->
            // 5.1 check file_licenses
            if (curationFileItem.fileLicenses?.filter { modifiers.elementAt(0).contains(it.modifier) }?.size !=
                curationFileItem.fileLicenses?.size) {
                logger.log("$errorPrefix unallowed package_modifier/modifier combination found in " +
                        "file_licenses! $errorSuffix", Level.WARN, phase = ProcessingPhase.CURATION
                )
                return false
            }
            // 5.2 check copyright_licenses
            if (curationFileItem.fileCopyrights?.filter { modifiers.elementAt(1).contains(it.modifier) }?.size !=
                curationFileItem.fileCopyrights?.size) {
                logger.log("$errorPrefix unallowed package_modifier/modifier combination found in " +
                        "copyright_licenses! $errorSuffix", Level.WARN, phase = ProcessingPhase.CURATION
                )
                return false
            }
        }
        // 6. file_scope: must not be empty string
        packageCuration.curations?.forEach { curationFileItem ->
            if (curationFileItem.fileScope == "") {
                logger.log("$errorPrefix file_scope must be non-empty $errorSuffix", Level.WARN,
                    phase = ProcessingPhase.CURATION
                )
                return false
            }
        }
        // 7. file_scope: glob patterns only allowed for modifier: update, delete
        packageCuration.curations?.forEach { curationFileItem ->
            if (containsGlobPatternSymbol(curationFileItem.fileScope)) {
                curationFileItem.fileLicenses?.forEach {
                    if (it.modifier == "insert") {
                        logger.log("$errorPrefix file_scope contains a glob-pattern-symbol! Not allowed when " +
                                "modifier is insert $errorSuffix", Level.WARN, phase = ProcessingPhase.CURATION
                        )
                        return false
                    }
                }
                curationFileItem.fileCopyrights?.forEach {
                    if (it.modifier == "insert") {
                        logger.log("$errorPrefix file_scope contains a glob-pattern-symbol! Not allowed when " +
                                "modifier is insert $errorSuffix", Level.WARN, phase = ProcessingPhase.CURATION
                        )
                        return false
                    }
                }
            }
        }
        // 8. check file_licenses: license + licenseTextInArchive combinations
        packageCuration.curations?.forEach { curationFileItem ->
            curationFileItem.fileLicenses?.forEach {
                if (it.modifier == "insert" && it.license == null) {
                    logger.log("$errorPrefix modifier(insert)/license = null is not allowed in " +
                            "file_scope: <${curationFileItem.fileScope}>  not valid! $errorSuffix", Level.WARN,
                        phase = ProcessingPhase.CURATION
                    )
                    return false
                }
                if (it.modifier == "insert" && it.license == "*") {
                    logger.log("$errorPrefix modifier(insert)/license combination in file_licensings of " +
                        "file_scope: <${curationFileItem.fileScope}>  not valid! $errorSuffix", Level.WARN,
                        phase = ProcessingPhase.CURATION
                    )
                    return false
                }
                if (it.modifier == "insert" && it.licenseTextInArchive == "*") {
                    logger.log("$errorPrefix modifier(insert)/license_text_in_archive=\"*\" combination in " +
                            "file_licensings of file_scope: <${curationFileItem.fileScope}>  not valid! $errorSuffix",
                        Level.WARN, phase = ProcessingPhase.CURATION
                    )
                    return false
                }
                if (it.modifier != "delete" && it.licenseTextInArchive != null && it.licenseTextInArchive != "*") {
                    if (!File(fileStore.path + "/" + it.licenseTextInArchive).exists()) {
                        logger.log("$errorPrefix file <${it.licenseTextInArchive}> does not exist in " +
                                "configured file store found in file_scope: <${curationFileItem.fileScope}>! " +
                                errorSuffix, Level.WARN, phase = ProcessingPhase.CURATION
                        )
                        return false
                    }
                }
            }
        }
        // 9. check copyright_licenses combinations
        packageCuration.curations?.forEach { curationFileItem ->
            curationFileItem.fileCopyrights?.forEach {
                when (it.modifier) {
                    "insert" -> if (it.copyright == null || it.copyright == "") {
                        logger.log(
                            "$errorPrefix Copyrights modifier: insert: neither null nor empty string" +
                                    " is allowed!: <${curationFileItem.fileScope}>! $errorSuffix", Level.WARN,
                             phase = ProcessingPhase.CURATION
                        )
                        return false
                    }
                    "delete" -> {
                        if (it.copyright == null || it.copyright == "") {
                            logger.log(
                                "$errorPrefix Copyrights modifier: delete: only string or string pattern" +
                                        " are allowed!: <${curationFileItem.fileScope}>! $errorSuffix", Level.WARN,
                                phase = ProcessingPhase.CURATION
                            )
                            return false
                        }
                        if (it.copyright.contains("**")) {
                            logger.log(
                                "$errorPrefix Copyrights modifier: delete: string pattern <**> is not allowed!:" +
                                        " <${curationFileItem.fileScope}>! $errorSuffix", Level.WARN,
                                phase = ProcessingPhase.CURATION
                            )
                            return false
                        }
                    }
                    "delete-all" -> if (it.copyright != null) {
                        logger.log(
                            "$errorPrefix Copyrights modifier: delete-all: only null is allowed: " +
                                    " <${curationFileItem.fileScope}>! $errorSuffix", Level.WARN,
                            phase = ProcessingPhase.CURATION
                        )
                        return false
                    }
                }
            }
        }
        // 10. check REUSE compliance
        if (packageCuration.packageModifier == "insert" && isReuseCompliant(packageCuration)) {
            // 10.1 check more than one license per file in LICENSES folder is not allowed
            packageCuration.curations?.groupBy { it.fileScope }?.forEach { (_, value) ->
                val curationFileLicenseItems = mutableListOf<CurationFileLicenseItem>()
                value.forEach {
                    it.fileLicenses?.let { it1 -> curationFileLicenseItems.addAll(it1) }
                }
                if (curationFileLicenseItems.size > 1) {
                    logger.log("$errorPrefix if package_modifier = \"insert\" for REUSE package: more than one " +
                            "license per file in LICENSES folder is not allowed! $errorSuffix", Level.WARN,
                        phase = ProcessingPhase.CURATION
                    )
                    return false
                }
            }
            // 10.2 licenseTextInArchive must be NOT null for files in LICENSES folder
            packageCuration.curations?.filter { it.fileScope.startsWith(getLicensesFolderPrefix(""))
                }?.forEach { curationFileItem1 ->
                if (curationFileItem1.fileLicenses?.any { it.licenseTextInArchive == null } == true) {
                    logger.log("$errorPrefix if package_modifier = \"insert\" for REUSE package: " +
                            "licenseTextInArchive = null is not allowed for files in LICENSES folder! $errorSuffix",
                        Level.WARN, phase = ProcessingPhase.CURATION
                    )
                    return false
                }
            }
            // 10.3 licenseTextInArchive must be null for files outside of the LICENSES folder
            packageCuration.curations?.filter { !it.fileScope.startsWith(getLicensesFolderPrefix(""))
                }?.forEach { curationFileItem1 ->
                if (curationFileItem1.fileLicenses?.any { it.licenseTextInArchive != null } == true) {
                    logger.log("$errorPrefix if package_modifier = \"insert\" for REUSE package: licenseText" +
                            "InArchive must be null for files outside of the LICENSES folder! $errorSuffix",
                        Level.WARN, phase = ProcessingPhase.CURATION
                    )
                    return false
                }
            }
        }
        // 11. check "resolvedIssues"
        if ((packageCuration.packageModifier == "insert" || packageCuration.packageModifier == "delete") &&
                    !packageCuration.resolvedIssues.isNullOrEmpty()) {
            logger.log(
                "$errorPrefix When package_modifier == insert or delete, the resolved_issues " +
                        "list must be null or empty! $errorSuffix", Level.WARN, phase = ProcessingPhase.CURATION
            )
            return false
        }
        val pattern = "(E|W|I)\\d\\d".toRegex()
        if (packageCuration.packageModifier == "update" && !packageCuration.resolvedIssues.isNullOrEmpty()) {
            packageCuration.resolvedIssues.forEach {
                if (!pattern.matches(it)) {
                    logger.log(
                        "$errorPrefix Issue-ID \"$it\" not a valid format! $errorSuffix",
                        Level.WARN, phase = ProcessingPhase.CURATION
                    )
                    return false
                }
            }
        }
        return true
    }

    /**
     * A project/package is REUSE compliant if a folder with the name "LICENSES" exists.
     */
    private fun isReuseCompliant(packageCuration: PackageCuration): Boolean =
        packageCuration.curations?.any {
            it.fileScope.startsWith(getLicensesFolderPrefix(""))
        } ?: false

    private fun containsGlobPatternSymbol(path: String): Boolean =
        (path.contains('*') || path.contains('?') || path.contains('[') || path.contains(']') ||
                path.contains('{') || path.contains('}') || path.contains('!'))

    /**
     * The method checks if a given [id] is valid: [id] must consist of type, namespace and name - version
     * may be empty, or contain a valid IVY expression.
     */
    private fun packageIdIsValid(id: Identifier): Boolean {
        val ret = true
        // special case, if there is no package manager like in the REUSE example
        if (id.type == "Unmanaged") return ret

        if (id.name == "" || id.namespace == "" || id.type == "") return false
        @Suppress("SwallowedException")
        if (id.version != "") {
            return try {
                // set dummy version "1.1.1", in order to force the satisfies method to an exception in case that the
                // id.version is no allowed ivy-expression
                val pkgIvyVersion = Semver("1.1.1", Semver.SemverType.IVY)
                pkgIvyVersion.satisfies(id.version)
                true
            } catch (e: SemverException) {
                false
            }
        }
        return ret
    }
}
