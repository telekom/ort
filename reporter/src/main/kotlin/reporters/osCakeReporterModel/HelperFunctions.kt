/*
 * Copyright (C) 2021 Deutsche Telekom AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import java.io.File
import java.lang.StringBuilder
import java.nio.file.FileSystems

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression

internal const val FOUND_IN_FILE_SCOPE_DECLARED = "[DECLARED]"
internal const val REUSE_LICENSES_FOLDER = "LICENSES/"
internal const val CURATION_DEFAULT_LICENSING = "<DEFAULT_LICENSING>"
internal const val CURATION_LOGGER = "OSCakeCuration"
internal const val REPORTER_LOGGER = "OSCakeReporter"

val commentPrefixRegexList = listOf("""\*""", """/\*+""", "//", "#", "<#", """\.\.\.""", "REM",
    "<!--", "!", "'", "--", ";", """\(\*""", """\{""").generatePrefixRegex()
val commentSuffixRegexList = listOf("""\*+/""", "-->", "#>", """\*\)""", """\}""").generateSuffixRegex()

/**
 * The [packageModifierMap] is a Hashmap which defines the allowed packageModifier (=key) and their associated
 * modifiers - the first set contains modifiers for licenses, second set for copyrights
 * Important: the sequence of items in the sets defines also the sequence of curations
 * e.g.: for packageModifier: "update" the sequence of curations is "delete-all", than "delete" and finally "insert"
 */
internal val packageModifierMap = hashMapOf("delete" to listOf(setOf(), setOf()),
    "insert" to listOf(setOf("insert"), setOf("insert")),
    "update" to listOf(setOf("delete", "insert", "update"), setOf("delete-all", "delete", "insert"))
)

/**
 * [orderLicenseByModifier] defines the sort order of curations for licenses.
 */
internal val orderLicenseByModifier = packageModifierMap.map { it.key to packageModifierMap.get(it.key)?.get(0)?.
withIndex()?.associate { it.value to it.index } }.toMap()

/**
 * [orderCopyrightByModifier] defines the sort order of curations for copyrights.
 */
internal val orderCopyrightByModifier = packageModifierMap.map { it.key to packageModifierMap.get(it.key)?.get(1)?.
withIndex()?.associate { it.value to it.index } }.toMap()

/**
 * Checks if a license is categorized as "instanced" license - as defined in file
 * "license-classifications.yml" (passed as program parameter).
 */
internal fun isInstancedLicense(input: ReporterInput, license: String): Boolean =
    input.licenseClassifications.licensesByCategory.getOrDefault("instanced",
        setOf<SpdxSingleLicenseExpression>()).map { it.simpleLicense().toString() }.contains(license)

/**
 * If a file with [path] already exists, a suffix is prepared for uniqueness and the adapted path [ret] is returned.
 */
internal fun deduplicateFileName(path: String): String {
    var ret = path
    if (File(path).exists()) {
        var counter = 2
        while (File(path + "_" + counter).exists()) {
            counter++
        }
        ret = path + "_" + counter
    }
    return ret
}

internal fun getLicensesFolderPrefix(packageRoot: String) = packageRoot +
        (if (packageRoot != "") "/" else "") + REUSE_LICENSES_FOLDER

internal fun createPathFlat(id: Identifier, path: String, fileExtension: String? = null): String =
    id.toPath("%") + "%" + path.replace('/', '%').replace('\\', '%'
    ) + if (fileExtension != null) ".$fileExtension" else ""

/**
 * Depending on the [path] of the file and the name of the file (contained in list [scopePatterns]) the
 * [scopeLevel] is identified.
 */
internal fun getScopeLevel(path: String, packageRoot: String, scopePatterns: List<String>): ScopeLevel {
    var scopeLevel = ScopeLevel.FILE
    val fileSystem = FileSystems.getDefault()

    if (!scopePatterns.filter { fileSystem.getPathMatcher(
            "glob:$it"
        ).matches(File(File(path).name).toPath()) }.isNullOrEmpty()) {

        scopeLevel = ScopeLevel.DIR
        var fileName = path
        if (path.startsWith(packageRoot) && packageRoot != "") fileName =
            path.replace(packageRoot, "").replaceFirst("/", "")
        if (fileName.split("/").size == 1) scopeLevel = ScopeLevel.DEFAULT
    }
    return scopeLevel
}

internal fun getDirScopePath(pack: Pack, fileScope: String): String {
    val p = if (fileScope.startsWith(pack.packageRoot) && pack.packageRoot != "") {
        fileScope.replaceFirst(pack.packageRoot, "")
    } else {
        fileScope
    }
    val lastIndex = p.lastIndexOf("/")
    var start = 0
    if (p[0] == '/' || p[0] == '\\') start = 1
    return p.substring(start, lastIndex)
}

internal fun getPathWithoutPackageRoot(pack: Pack, fileScope: String): String {
    val pathWithoutPackage = if (fileScope.startsWith(pack.packageRoot) && pack.packageRoot != "") {
        fileScope.replaceFirst(pack.packageRoot, "")
    } else {
        fileScope
    }.replace("\\", "/")
    if (pathWithoutPackage.startsWith("/")) return pathWithoutPackage.substring(1)
    return pathWithoutPackage
}

internal fun getPathName(pack: Pack, fib: FileInfoBlock): String {
    var rc = fib.path
    if (pack.packageRoot != "") rc = fib.path.replaceFirst(pack.packageRoot, "")
    if (rc[0].equals('/') || rc[0].equals('\\')) rc = rc.substring(1)
    if (pack.reuseCompliant && rc.endsWith(".license")) {
        val pos = rc.indexOfLast { it == '.' }
        rc = rc.substring(0, pos)
    }
    return rc
}

/**
 * The method walks through the packages, searches for corresponding issues (stored in [logger]) and sets
 * the flags "hasIssues" (on package and on global level).
 */
internal fun handleOSCakeIssues(project: Project, logger: OSCakeLogger) {
    var hasIssuesGlobal = false
    // create Map with key = package (package may also be null)
    val issuesPerPackage = logger.osCakeIssues.groupBy { it.id?.toCoordinates() }
    // walk through packs and check if there are issues (WARN and ERROR) per package - set global hasIssues if necessary
    project.packs.forEach { pack ->
        pack.hasIssues = issuesPerPackage[pack.id.toCoordinates()]?.any {
            it.level == Level.WARN || it.level == Level.ERROR } ?: false
        if (pack.hasIssues) hasIssuesGlobal = true
    }
    // check OSCakeIssues with no package info
    if (!hasIssuesGlobal) hasIssuesGlobal = issuesPerPackage.get(null)?.any {
        it.level == Level.WARN || it.level == Level.ERROR } ?: false

    // set global hasIssues
    project.hasIssues = hasIssuesGlobal
}

internal fun deleteFromArchive(oldPath: String?, archiveDir: File) =
    oldPath?.let { File(archiveDir, oldPath).apply { if (exists()) delete() } }

internal fun List<String>.generatePrefixRegex(): String =
    StringBuilder("^(").also { sb ->
        this.forEachIndexed { index, element ->
            if (index > 0) sb.append("|")
            sb.append("($element *)")
        }
        sb.append(")")
    }.toString()

internal fun List<String>.generateSuffixRegex(): String =
    StringBuilder().also { sb ->
        this.forEachIndexed { index, element ->
            if (index > 0) sb.append("|")
            sb.append("( *$element)")
        }
        sb.append("$")
    }.toString()

internal fun String.getRidOfCommentSymbols(): String {
    val withoutPrefix = Regex(commentPrefixRegexList).replace(this.trim(), "")
    return if (withoutPrefix != this.trim()) Regex(commentSuffixRegexList).replace(withoutPrefix, "") else withoutPrefix
}
