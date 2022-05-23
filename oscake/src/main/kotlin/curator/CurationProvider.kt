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

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.oscake.CURATION_LOGGER
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.oscake.common.ActionProvider
import org.ossreviewtoolkit.oscake.packageModifierMap
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.ProcessingPhase
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.getLicensesFolderPrefix

/**
 * The [CurationProvider] gets the locations where to find
 * - the yml-files containing curations (their semantics is checked while processing) and
 * - the corresponding license text files
 * and creates a list of possible [CurationPackage]s.
 */
internal class CurationProvider(
    /**
     * Contains the root folder containing curation files in yml format (possibly organized in subdirectories).
     */
    curationDirectory: File,
    /**
     *  Contains the root folder containing files with license texts (possibly organized in subdirectories).
     */
    fileStore: File
) : ActionProvider(curationDirectory, fileStore, CURATION_LOGGER, CurationPackage::class, ProcessingPhase.CURATION) {
    /**
     * List of available [CurationPackage]s
     */
    // internal val curationPackages = mutableListOf<CurationPackage>()

    private var errorPrefix = ""
    private val errorSuffix = " --> curation ignored"
    /**
     * Checks semantics of the [item]. In case of incongruities, these are logged and the curation is
     * not applied.
     */
    @Suppress("ComplexMethod", "LongMethod")
    // abstract fun checkSemantics(item: ActionPackage, fileName: String, fileStore: File?): Boolean
    override fun checkSemantics(item: ActionPackage, fileName: String, fileStore: File?): Boolean {
        item as CurationPackage
        errorPrefix = "[Semantics] - File: $fileName [${item.id.toCoordinates()}]: "

        // 1. check Package-ID
        if (!packageIdIsValid(item.id)) return loggerWarn("package <id> is not valid!")

        // 2. check packageModifier
        if (!packageModifierMap.containsKey(item.packageModifier))
            return loggerWarn("package_modifier <${item.packageModifier}> not valid! ")

        // 3. no curations allowed for packageModifier: delete
        if (item.packageModifier == "delete" && !item.curations.isNullOrEmpty())
            return loggerWarn("if package_modifier = \"delete\" no curations are allowed!")

        // 4. repository must be defined for packageModifier: insert
        if (item.packageModifier == "insert" && item.repository == null)
            return loggerWarn("if package_modifier = \"insert\" repository must be defined!")

        // 5. check modifiers in every licensing
        val modifiers = packageModifierMap.getOrElse(item.packageModifier) { listOf(setOf(), setOf()) }
        item.curations?.forEach { curationFileItem ->
            // 5.1 check file_licenses
            if (curationFileItem.fileLicenses?.filter { modifiers.elementAt(0).contains(it.modifier) }?.size !=
                curationFileItem.fileLicenses?.size
            ) return loggerWarn("prohibited package_modifier/modifier combination found in file_licenses!")

            // 5.2 check copyright_licenses
            if (curationFileItem.fileCopyrights?.filter { modifiers.elementAt(1).contains(it.modifier) }?.size !=
                curationFileItem.fileCopyrights?.size
            ) return loggerWarn("prohibited package_modifier/modifier combination found in copyright_licenses!")
        }

        // 6. file_scope: must not be empty string
        item.curations?.forEach { curationFileItem ->
            if (curationFileItem.fileScope == "") return loggerWarn("file_scope must be non-empty")
        }
        // 7. file_scope: glob patterns only allowed for modifier: update, delete
        item.curations?.forEach { curationFileItem ->
            if (containsGlobPatternSymbol(curationFileItem.fileScope)) {
                curationFileItem.fileLicenses?.forEach {
                    if (it.modifier == "insert")
                        return loggerWarn(
                            "file_scope contains a glob-pattern-symbol! Not allowed when modifier is insert"
                        )
                }
                curationFileItem.fileCopyrights?.forEach {
                    if (it.modifier == "insert")
                        return loggerWarn(
                            "file_scope contains a glob-pattern-symbol! Not allowed when modifier is insert"
                        )
                }
            }
        }
        // 8. check file_licenses: license + licenseTextInArchive combinations
        item.curations?.forEach { curationFileItem ->
            curationFileItem.fileLicenses?.forEach {
                if (it.modifier == "insert" && it.license == null)
                    return loggerWarn(
                        "modifier(insert)/license = null is not allowed in file_scope: " +
                                "<${curationFileItem.fileScope}> not valid!"
                    )
                if (it.modifier == "insert" && it.license == "*")
                    return loggerWarn(
                        "modifier(insert)/license combination in file_licensings of " +
                            "file_scope: <${curationFileItem.fileScope}>  not valid!"
                    )
                if (it.modifier == "insert" && it.licenseTextInArchive == "*")
                    return loggerWarn(
                        "modifier(insert)/license_text_in_archive=\"*\" combination in " +
                            "file_licensings of file_scope: <${curationFileItem.fileScope}>  not valid!"
                    )
                if (it.modifier != "delete" && it.licenseTextInArchive != null && it.licenseTextInArchive != "*")
                    if (!File(fileStore!!.path + "/" + it.licenseTextInArchive).exists())
                        return loggerWarn(
                            "file <${it.licenseTextInArchive}> does not exist in " +
                                "configured file store found in file_scope: <${curationFileItem.fileScope}>! "
                        )
            }
        }
        // 9. check copyright_licenses combinations
        item.curations?.forEach { curationFileItem ->
            curationFileItem.fileCopyrights?.forEach {
                when (it.modifier) {
                    "insert" -> if (it.copyright == null || it.copyright == "")
                        return loggerWarn(
                            "Copyrights modifier: insert: neither null nor empty string" +
                                    " is allowed!: <${curationFileItem.fileScope}>!"
                        )
                    "delete" -> {
                        if (it.copyright == null || it.copyright == "")
                            return loggerWarn(
                                "Copyrights modifier: delete: only string or string pattern" +
                                        " are allowed!: <${curationFileItem.fileScope}>!"
                            )
                        if (it.copyright.contains("**"))
                            return loggerWarn(
                                "Copyrights modifier: delete: string pattern <**> is not allowed!:" +
                                        " <${curationFileItem.fileScope}>!"
                            )
                    }
                    "delete-all" -> if (it.copyright != null)
                        return loggerWarn(
                            "Copyrights modifier: delete-all: only null is allowed: <${curationFileItem.fileScope}>!"
                        )
                }
            }
        }
        // 10. check REUSE compliance
        if (item.packageModifier == "insert" && isReuseCompliant(item)) {
            // 10.1 check more than one license per file in LICENSES folder is not allowed
            item.curations?.groupBy { it.fileScope }?.forEach { (_, value) ->
                val curationFileLicenseItems = mutableListOf<CurationFileLicenseItem>()
                value.forEach {
                    it.fileLicenses?.let { it1 -> curationFileLicenseItems.addAll(it1) }
                }
                if (curationFileLicenseItems.size > 1)
                    return loggerWarn(
                        "if package_modifier = \"insert\" for REUSE package: more than one " +
                            "license per file in LICENSES folder is not allowed!"
                    )
            }
            // 10.2 licenseTextInArchive must be NOT null for files in LICENSES folder
            item.curations?.filter {
                    it.fileScope.startsWith(getLicensesFolderPrefix(""))
                }?.forEach { curationFileItem1 ->
                if (curationFileItem1.fileLicenses?.any { it.licenseTextInArchive == null } == true)
                    return loggerWarn(
                        "if package_modifier = \"insert\" for REUSE package: " +
                            "licenseTextInArchive = null is not allowed for files in LICENSES folder!"
                    )
            }
            // 10.3 licenseTextInArchive must be null for files outside the LICENSES folder
            item.curations?.filter {
                !it.fileScope.startsWith(getLicensesFolderPrefix(""))
            }?.forEach { curationFileItem1 ->
                if (curationFileItem1.fileLicenses?.any { it.licenseTextInArchive != null } == true)
                    return loggerWarn(
                        "if package_modifier = \"insert\" for REUSE package: licenseText" +
                            "InArchive must be null for files outside of the LICENSES folder!"
                    )
            }
        }
        // 11. check "resolvedIssues"
        if (
            (item.packageModifier == "insert" || item.packageModifier == "delete") &&
            !item.resolvedIssues.isNullOrEmpty()
        ) return loggerWarn(
            "When package_modifier == insert or delete, the resolved_issues list must be null or empty!"
        )

        val pattern = "([EWI])\\d\\d".toRegex()
        if (item.packageModifier == "update" && !item.resolvedIssues.isNullOrEmpty())
            item.resolvedIssues.forEach {
                if (!pattern.matches(it) && it != "W*" && it != "E*")
                    return loggerWarn("Issue-ID \"$it\" not a valid format!")
            }
        return true
    }

    /**
     * A project/package is REUSE compliant if a folder with the name "LICENSES" exists.
     */
    private fun isReuseCompliant(curationPackage: CurationPackage): Boolean =
        curationPackage.curations?.any {
            it.fileScope.startsWith(getLicensesFolderPrefix(""))
        } ?: false

    private fun containsGlobPatternSymbol(path: String): Boolean =
        (
            path.contains('*') || path.contains('?') || path.contains('[') || path.contains(']') ||
            path.contains('{') || path.contains('}') || path.contains('!')
        )

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

    private fun loggerWarn(msg: String): Boolean {
        logger.log("$errorPrefix $msg $errorSuffix", Level.WARN, phase = ProcessingPhase.CURATION)
        return false
    }
}
