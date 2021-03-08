/*
 * Copyright (C)  tbd
 */
package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException
import java.io.File
import java.io.IOException
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.readValue

/* Hashmap which defines the allowed packageModifier (=key) and their associated modifiers -
   the first set contains modifiers for licenses, second set for copyrights
   Important: the sequence of items in the sets defines also the sequence of curations
   e.g.: for packageModifier: "update" the sequence of curations is "delete-all", than "delete" and finally "insert"
 */
internal val packageModifierMap = hashMapOf("delete" to listOf(setOf(), setOf()),
    "insert" to listOf(setOf("insert"), setOf("insert")),
    "update" to listOf(setOf("delete", "insert", "update"), setOf("delete-all", "delete", "insert"))
)
// defines the sort order of curations for licenses
internal val orderLicenseByModifier = packageModifierMap.map { it.key to packageModifierMap.get(it.key)?.get(0)?.
    withIndex()?.associate { it.value to it.index } }.toMap()

// defines the sort order of curations for copyrights
internal val orderCopyrightByModifier = packageModifierMap.map { it.key to packageModifierMap.get(it.key)?.get(1)?.
    withIndex()?.associate { it.value to it.index } }.toMap()

class CurationProvider(curationDirectory: File, fileStore: File) {
    internal val packageCurations = mutableListOf<PackageCuration>()
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger("OSCakeCuration") }

    init {
        if (curationDirectory.isDirectory) {
            curationDirectory.walkTopDown().filter { it.isFile && it.extension == "yml" }.forEach {
                try {
                    it.readValue<List<PackageCuration>>().forEach { packageCuration ->
                        if (checkSemantics(packageCuration, it.name, fileStore)) packageCurations.add(packageCuration)
                    }
                } catch (e: IOException) {
                    logger.log("Error while processing file: ${it.absoluteFile}! - Curation not applied!",
                        Severity.ERROR)
                    e.message?.let { logger.log(e.message!!, Severity.ERROR) }
                }
            }
        }
    }

    internal fun getCurationFor(pkgId: Identifier): PackageCuration? {
        packageCurations.filter { it.isApplicable(pkgId) }.apply {
            if (size > 1) logger.log("Error: more than one curation was found for" +
                    " package: $pkgId - don't know which one to take!", Severity.ERROR)
            if (size != 1) return null
            return first()
        }
    }

    @Suppress("ComplexMethod")
    private fun checkSemantics(packageCuration: PackageCuration, fileName: String, fileStore: File): Boolean {
        val errorPrefix = "File: $fileName [${packageCuration.id.toCoordinates()}]: "
        val errorSuffix = " --> curation ignored"

        // 1. check Package-ID
        if (!packageIdIsValid(packageCuration.id)) {
            logger.log("$errorPrefix package id is not valid! $errorSuffix", Severity.WARNING)
            return false
        }
        // 2. check packageModifier
        if (!packageModifierMap.containsKey(packageCuration.packageModifier)) {
            logger.log("$errorPrefix package_modifier <${packageCuration.packageModifier}> not valid! " +
                    "$errorSuffix", Severity.WARNING)
            return false
        }
        // 3. no curations allowed for packageModifier: delete
        if (packageCuration.packageModifier == "delete" && !packageCuration.curations.isNullOrEmpty()) {
            logger.log("$errorPrefix if package_modifier = \"delete\" no curations are allowed! $errorSuffix",
                Severity.WARNING)
            return false
        }
        // 4. repository must be defined for packageModifier: insert
        if (packageCuration.packageModifier == "insert" && packageCuration.repository == null) {
            logger.log("$errorPrefix if package_modifier = \"insert\" repository must be defined! $errorSuffix",
                Severity.WARNING)
            return false
        }
        // 5. check modifiers in every licensing
        val modifiers = packageModifierMap.getOrElse(packageCuration.packageModifier) { listOf(setOf(), setOf()) }
        packageCuration.curations?.forEach { curationFileItem ->
            // 5.1 check file_licenses
            if (curationFileItem.fileLicenses?.filter { modifiers.elementAt(0).contains(it.modifier) }?.size !=
                curationFileItem.fileLicenses?.size) {
                logger.log("$errorPrefix unallowed package_modifier/modifier combination found in " +
                        "file_licenses! $errorSuffix", Severity.WARNING)
                return false
            }
            // 5.2 check copyright_licenses
            if (curationFileItem.fileCopyrights?.filter { modifiers.elementAt(1).contains(it.modifier) }?.size !=
                curationFileItem.fileCopyrights?.size) {
                logger.log("$errorPrefix unallowed package_modifier/modifier combination found in " +
                        "copyright_licenses! $errorSuffix", Severity.WARNING)
                return false
            }
        }
        // 6. file_scope: must not be empty string
        packageCuration.curations?.forEach { curationFileItem ->
            if (curationFileItem.fileScope == "") {
                logger.log("$errorPrefix file_scope must be non-empty $errorSuffix", Severity.WARNING)
                return false
            }
        }
        // 7. file_scope: glob patterns only allowed for modifier: update, delete
        packageCuration.curations?.forEach { curationFileItem ->
            if (containsGlobPatternSymbol(curationFileItem.fileScope)) {
                curationFileItem.fileLicenses?.forEach {
                    if (it.modifier == "insert") {
                        logger.log("$errorPrefix file_scope contains a glob-pattern-symbol! Not allowed when " +
                                "modifier is insert $errorSuffix", Severity.WARNING)
                        return false
                    }
                }
                curationFileItem.fileCopyrights?.forEach {
                    if (it.modifier == "insert") {
                        logger.log("$errorPrefix file_scope contains a glob-pattern-symbol! Not allowed when " +
                                "modifier is insert $errorSuffix", Severity.WARNING)
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
                            "file_scope: <${curationFileItem.fileScope}>  not valid! $errorSuffix", Severity.WARNING)
                    return false
                }
                if (it.modifier == "insert" && it.license == "*") {
                    logger.log("$errorPrefix modifier(insert)/license combination in file_licensings of " +
                        "file_scope: <${curationFileItem.fileScope}>  not valid! $errorSuffix", Severity.WARNING)
                    return false
                }
                if (it.modifier == "insert" && it.licenseTextInArchive == "*") {
                    logger.log("$errorPrefix modifier(insert)/license_text_in_archive=\"*\" combination in " +
                            "file_licensings of file_scope: <${curationFileItem.fileScope}>  not valid! $errorSuffix",
                        Severity.WARNING)
                    return false
                }
                if (it.modifier != "delete" && it.licenseTextInArchive != null && it.licenseTextInArchive != "*") {
                    if (!File(fileStore.path + "/" + it.licenseTextInArchive).exists()) {
                        logger.log("$errorPrefix file <${it.licenseTextInArchive} does not exist in " +
                                "configured file store found in file_scope: <${curationFileItem.fileScope}>! " +
                                "$errorSuffix", Severity.WARNING)
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
                        logger.log("$errorPrefix Copyrights modifier: insert: neither null nor empty string" +
                                " is allowed!: <${curationFileItem.fileScope}>! $errorSuffix", Severity.WARNING)
                        return false
                    }
                    "delete" -> {
                        if (it.copyright == null || it.copyright == "") {
                            logger.log(
                                "$errorPrefix Copyrights modifier: delete: only string or string pattern" +
                                    " are allowed!: <${curationFileItem.fileScope}>! $errorSuffix", Severity.WARNING
                            )
                            return false
                        }
                        if (it.copyright.contains("**")) {
                            logger.log(
                                "$errorPrefix Copyrights modifier: delete: string pattern <**> is not allowed!:" +
                                        " <${curationFileItem.fileScope}>! $errorSuffix", Severity.WARNING
                            )
                            return false
                        }
                    }
                    "delete-all" -> if (it.copyright != null) {
                        logger.log(
                            "$errorPrefix Copyrights modifier: delete-all: only null is allowed: " +
                                    " <${curationFileItem.fileScope}>! $errorSuffix", Severity.WARNING
                        )
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun containsGlobPatternSymbol(path: String): Boolean =
        (path.contains('*') || path.contains('?') || path.contains('[') || path.contains(']') ||
                path.contains('{') || path.contains('}') || path.contains('!'))

    // id must consist of type, namespace and name - version may be empty, or is a valid IVY expression
    private fun packageIdIsValid(id: Identifier): Boolean {
        val ret = true
        if (id.name == "" || id.namespace == "" || id.type == "") return false
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
