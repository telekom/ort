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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File
import java.io.IOException
import java.lang.StringBuilder
import java.nio.file.FileSystems
import java.security.MessageDigest

import kotlin.system.exitProcess

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.packZip
import org.ossreviewtoolkit.utils.common.toHexString
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

const val FOUND_IN_FILE_SCOPE_DECLARED = "[DECLARED]"
const val REUSE_LICENSES_FOLDER = "LICENSES/"
internal const val REPORTER_LOGGER = "OSCakeReporter"

val commentPrefixRegexList = listOf("""\*""", """/\*+""", "//", "#", "<#", """\.\.\.""", "REM",
    "<!--", "!", "'", "--", ";", """\(\*""", """\{""").generatePrefixRegex()
val commentSuffixRegexList = listOf("""\*+/""", "-->", "#>", """\*\)""", """\}""").generateSuffixRegex()

/**
 * Checks if a license is categorized as "instanced" license - as defined in file
 * "license-classifications.yml" (passed as program parameter).
 */
internal fun isInstancedLicense(input: ReporterInput, license: String): Boolean =
    input.licenseClassifications.licensesByCategory.getOrDefault("instanced",
        setOf<SpdxSingleLicenseExpression>()).map { it.simpleLicense().toString() }.contains(license)

/**
 * If a file with [path] already exists, a suffix is prepared for uniqueness and the adapted path is returned.
 */
fun deduplicateFileName(path: String): String {
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

fun getLicensesFolderPrefix(packageRoot: String) = packageRoot +
        (if (packageRoot != "") "/" else "") + REUSE_LICENSES_FOLDER

fun createPathFlat(id: Identifier, path: String, fileExtension: String? = null): String =
    id.toPath("%") + "%" + path.replace('/', '%').replace('\\', '%'
    ) + if (fileExtension != null) ".$fileExtension" else ""

/**
 * Depending on the [path] of the file and the name of the file (contained in list [scopePatterns]) the
 * [ScopeLevel] is identified.
 */
fun getScopeLevel(path: String, packageRoot: String, scopePatterns: List<String>,
                  scopeIgnorePatterns: List<String>, lowerCaseComparisonOfScopePatterns: Boolean): ScopeLevel {

    var scopeLevel = ScopeLevel.FILE
    val fileSystem = FileSystems.getDefault()

    val comparePath = if (lowerCaseComparisonOfScopePatterns) File(File(path).name.lowercase()).toPath() else
        File(File(path).name).toPath()

    if (scopeIgnorePatterns.isNotEmpty() && scopeIgnorePatterns.any {
            fileSystem.getPathMatcher("glob:$it").matches(comparePath) }) return scopeLevel

    if (!scopePatterns.filter { fileSystem.getPathMatcher("glob:$it").matches(comparePath) }.isNullOrEmpty()) {
        scopeLevel = ScopeLevel.DIR
        var fileName = path
        if (path.startsWith(packageRoot) && packageRoot != "") fileName =
            path.replace(packageRoot, "").replaceFirst("/", "")
        if (fileName.split("/").size == 1) scopeLevel = ScopeLevel.DEFAULT
    }
    return scopeLevel
}

fun getDirScopePath(pack: Pack, fileScope: String): String {
    val p = if (fileScope.startsWith(pack.packageRoot) && pack.packageRoot != "") {
        fileScope.replaceFirst(pack.packageRoot, "")
    } else {
        fileScope
    }
    val lastIndex = p.lastIndexOf("/")
    var start = 0
    if (p[0] == '/' || p[0] == '\\') start = 1
    return if (lastIndex >= start) p.substring(start, lastIndex) else ""
}

fun getPathWithoutPackageRoot(pack: Pack, fileScope: String): String {
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
 * the flags "hasIssues" (on package, root, default and dir level).
 */
fun handleOSCakeIssues(project: Project, logger: OSCakeLogger, issuesLevel: Int) {
    // create Map with key = package (package may also be null)
    val issuesPerPackage = logger.osCakeIssues.groupBy { it.id?.toCoordinates() }
    // keeps next number for the issue id, starting at 1
    val issueNumberPerPackage = issuesPerPackage.keys.associateBy({ it }, {
        mutableMapOf(
            "errno" to 1,
            "warno" to 1, "infno" to 1
        )
    }).toMutableMap()

    // Root-Level: handle OSCakeIssues with no package info
    issuesPerPackage[null]?.forEach {
        addIssue(it, project.issueList, issuesLevel, issueNumberPerPackage.get(null)!!)
    }

    // Package-Level, but package does not exist --> put into Root-Level
    val idList = issuesPerPackage.keys
    val packIdList = project.packs.map { it.id.toCoordinates() }
    idList.filterNot { packIdList.contains(it) || it == null }.forEach { idString ->
        issuesPerPackage[idString]?.forEach {
            addIssue(it, project.issueList, issuesLevel, issueNumberPerPackage.get(null)!!,
                it.id?.toCoordinates() ?: "")
        }
    }

    // Package-Level: handle OSCakeIssues with package but no reference info
    project.packs.forEach { pack ->
        issuesPerPackage[pack.id.toCoordinates()]?.forEach {
            if (it.reference == null || !(it.reference is DefaultLicense || it.reference is DirLicense)) {
                addIssue(it, pack.issueList, issuesLevel, issueNumberPerPackage.get(pack.id.toCoordinates())!!)
            }
        }
    }

    // Default/Dir-Level: handle OSCakeIssues with package and reference for Default/Dir level
    project.packs.forEach { pack ->
        issuesPerPackage[pack.id.toCoordinates()]?.forEach {
            when (it.reference) {
                is DefaultLicense -> {
                    addIssue(
                        it, it.reference.issueList, issuesLevel,
                        issueNumberPerPackage.get(pack.id.toCoordinates())!!
                    )
                }
                is DirLicense -> {
                    addIssue(
                        it, it.reference.issueList, issuesLevel,
                        issueNumberPerPackage.get(pack.id.toCoordinates())!!
                    )
                }
            }
        }
    }
    propagateHasIssues(project)
}

/**
 *  sets and propagates hasIssue from the different levels: DefaultLicensing, DirLicensing, Package to Project
 */
fun propagateHasIssues(project: Project) {
    var projectHasIssues = false
    project.packs.forEach { pack ->
        var pkgHasIssues = false
        pack.defaultLicensings.forEach {
            it.hasIssues = it.issueList.errors.isNotEmpty() || it.issueList.warnings.isNotEmpty()
            pkgHasIssues = pkgHasIssues || it.hasIssues
        }
        pack.dirLicensings.forEach { dirLicensing ->
            dirLicensing.licenses.forEach {
                it.hasIssues = it.issueList.errors.isNotEmpty() || it.issueList.warnings.isNotEmpty()
                pkgHasIssues = pkgHasIssues || it.hasIssues
            }
        }
        pkgHasIssues = pkgHasIssues || (pack.issueList.errors.isNotEmpty() || pack.issueList.warnings.isNotEmpty())
        pack.hasIssues = pkgHasIssues
        projectHasIssues = projectHasIssues || pack.hasIssues
    }
    project.hasIssues = projectHasIssues || (project.issueList.errors.isNotEmpty() ||
            project.issueList.warnings.isNotEmpty())
}

/**
 * Adds issue to the issues list depending on Level information;
 * return true for Warnings and Errors
 */
internal fun addIssue(oscakeIssue: OSCakeIssue, issueList: IssueList, issuesLevel: Int, no: MutableMap<String, Int>,
                      suffix: String = ""): Boolean {

    val prePrefix = if (oscakeIssue.phase == ProcessingPhase.CURATION) "Cur_" else ""

    return when (oscakeIssue.level) {
        Level.DEBUG -> false
        Level.INFO -> {
            if (issuesLevel > 1) issueList.infos.add(Issue(getNextNo('I', no, prePrefix, suffix), oscakeIssue.msg))
            false
        }
        Level.WARN -> { if (issuesLevel > 0) issueList.warnings.add(Issue(getNextNo('W', no, prePrefix, suffix),
            oscakeIssue.msg))
            true
        }
        Level.ERROR -> { if (issuesLevel > -1) issueList.errors.add(Issue(getNextNo('E', no, prePrefix, suffix),
            oscakeIssue.msg))
            true
        }
        else -> false
    }
}
/**
 * [getNextNo] returns the next id for an issue depending on the type: INFO, WARN, ERROR represented by the [prefix]
 * e.g. E01 or W01
 */
private fun getNextNo(prefix: Char, no: MutableMap<String, Int>, prePrefix: String, suffix: String = ""):
        String = when (prefix) {
        'I' -> {
            if (suffix.isEmpty()) {
                var next = no["infno"]!!
                no["infno"] = next + 1
                prePrefix + prefix.toString() + "%02d".format(next)
            } else {
                prefix.toString() + "_" + suffix
            }
        }
        'W' -> {
            if (suffix.isEmpty()) {
                var next = no["warno"]!!
                no["warno"] = next + 1
                prePrefix + prefix.toString() + "%02d".format(next)
            } else {
                prefix.toString() + "_" + suffix
            }
        }
        'E' -> {
            if (suffix.isEmpty()) {
                var next = no["errno"]!!
                no["errno"] = next + 1
                prePrefix + prefix.toString() + "%02d".format(next)
            } else {
                prefix.toString() + "_" + suffix
            }
        }
        else -> "No no found!"
    }

fun deleteFromArchive(oldPath: String?, archiveDir: File) =
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
    val withoutPrefix = Regex(commentPrefixRegexList).replaceFirst(this, "")
    return if (withoutPrefix != this) Regex(commentSuffixRegexList).replace(withoutPrefix, "") else withoutPrefix
}

private val SHA1_DIGEST by lazy { MessageDigest.getInstance("SHA-1") }

/**
 * [getHash] produces a unique hascode of the provenance of a package mainly used to expand the path of
 * a [Package] in a sourcecode directory.
 * E.g. {sourceCodeDir}/Maven/joda-time/joda-time/2.10.8/39b1a3cc0a54d8b34737520bc066d2baee2fe2a4
 */
internal fun getHash(provenance: Provenance): String {
    val key = when (provenance) {
        is ArtifactProvenance -> "${provenance.sourceArtifact.url}${provenance.sourceArtifact.hash.value}"
        is RepositoryProvenance -> {
            // The content on the archives does not depend on the VCS path in general, thus that path must not be part
            // of the storage key. However, for Git-Repo that path must be part of the storage key because it denotes
            // the Git-Repo manifest location rather than the path to be (sparse) checked out.
            val path = provenance.vcsInfo.path.takeIf { provenance.vcsInfo.type == VcsType.GIT_REPO }.orEmpty()
            "${provenance.vcsInfo.type}${provenance.vcsInfo.url}${provenance.resolvedRevision}$path"
        }
        is UnknownProvenance -> "unknownProvenance"
    }

    return SHA1_DIGEST.digest(key.toByteArray()).toHexString()
}

/**
 * The method is used for quality assurance only. It ensures that every referenced file is existing in the
 * archive and vice versa. Possible discrepancies are logged.
 */
fun compareLTIAwithArchive(project: Project, archiveDir: File, logger: OSCakeLogger, processingPhase: ProcessingPhase):
        Boolean {
    // consistency check: direction from pack to archive
    val missingFiles = mutableListOf<String>()
    project.packs.forEach { pack ->
        pack.defaultLicensings.filter { it.licenseTextInArchive != null &&
                !File(archiveDir.path + "/" + it.licenseTextInArchive).exists() }.forEach {
            missingFiles.add(it.licenseTextInArchive.toString())
        }
        pack.reuseLicensings.filter { it.licenseTextInArchive != null &&
                !File(archiveDir.path + "/" + it.licenseTextInArchive).exists() }.forEach {
            missingFiles.add(it.licenseTextInArchive.toString())
        }
        pack.dirLicensings.forEach { dirLicensing ->
            dirLicensing.licenses.filter { it.licenseTextInArchive != null &&
                    !File(archiveDir.path + "/" + it.licenseTextInArchive).exists() }.forEach {
                missingFiles.add(it.licenseTextInArchive.toString())
            }
        }
        pack.fileLicensings.forEach { fileLicensing ->
            if (fileLicensing.fileContentInArchive != null && !File(archiveDir.path + "/" +
                        fileLicensing.fileContentInArchive).exists()) {
                missingFiles.add(fileLicensing.fileContentInArchive!!)
            }
            fileLicensing.licenses.filter { it.licenseTextInArchive != null && !File(archiveDir.path +
                    "/" + it.licenseTextInArchive).exists() }.forEach {
                missingFiles.add(it.licenseTextInArchive.toString())
            }
        }
    }
    missingFiles.forEach {
        logger.log("File: \"${it}\" not found in archive! --> Inconsistency",
            Level.ERROR, phase = processingPhase
        )
    }
    if (missingFiles.isNotEmpty()) return true
    // consistency check: direction from archive to pack
    missingFiles.clear()
    archiveDir.listFiles()?.forEach { file ->
        var found = false
        val fileName = file.name
        project.packs.forEach { pack ->
            pack.defaultLicensings.filter { it.licenseTextInArchive != null }.forEach {
                if (it.licenseTextInArchive == fileName) found = true
            }
            pack.dirLicensings.forEach { dirLicensing ->
                dirLicensing.licenses.filter { it.licenseTextInArchive != null }.forEach {
                    if (it.licenseTextInArchive == fileName) found = true
                }
            }
            pack.fileLicensings.forEach { fileLicensing ->
                if (fileLicensing.fileContentInArchive != null && fileLicensing.fileContentInArchive == fileName) {
                    found = true
                }
                fileLicensing.licenses.filter { it.licenseTextInArchive != null }.forEach {
                    if (it.licenseTextInArchive == fileName) found = true
                }
            }
        }
        if (!found) missingFiles.add(fileName)
    }
    missingFiles.forEach {
        logger.log("Archived file: \"${it}\": no reference found in oscc-file! Inconsistency",
            Level.ERROR, phase = processingPhase
        )
    }
    if (missingFiles.isNotEmpty()) return true
    return false
}

fun osccToModel(osccFile: File, logger: OSCakeLogger, processingPhase: ProcessingPhase): OSCakeRoot {
    val mapper = jacksonObjectMapper()
    val osc = OSCakeRoot()
    var project: Project? = null
    try {
        val json = osccFile.readText()
        project = mapper.readValue<Project>(json)
    } catch (e: IOException) {
        logger.log("EXIT: Invalid json format found in file: \"$osccFile\".\n ${e.message} ",
            Level.ERROR, phase = processingPhase)
        exitProcess(3)
    } finally {
        if (project != null) {
            osc.project = project
            completeModel(osc.project)
        }
    }
    return osc
}

// rebuild info for model completeness built on information in oscc
private fun completeModel(project: Project) {
    project.packs.forEach { pack ->
        pack.namespace = pack.id.namespace
        pack.type = pack.id.type
        pack.defaultLicensings.forEach {
            it.declared = it.path == FOUND_IN_FILE_SCOPE_DECLARED
        }
    }
}

fun modelToOscc(project: Project, outputFile: File, logger: OSCakeLogger, processingPhase: ProcessingPhase): Boolean {
    val objectMapper = ObjectMapper()
    try {
        outputFile.bufferedWriter().use {
            it.write(objectMapper.writeValueAsString(project))
        }
    } catch (e: IOException) {
        logger.log("Error when writing json file: \"$outputFile\".\n ${e.message} ",
            Level.ERROR, phase = processingPhase)
        return true
    }
    return false
}

fun zipAndCleanUp(outputDir: File, tmpDirectory: File, zipFileName: String, logger: OSCakeLogger,
                  processingPhase: ProcessingPhase): Boolean {
    val targetFile = File(outputDir.path + "/" + zipFileName)
    if (targetFile.exists()) targetFile.delete()
    try {
        tmpDirectory.packZip(targetFile)
        tmpDirectory.deleteRecursively()
    } catch (e: IOException) {
        logger.log("Error during zip and cleanup!\n ${e.message} ", Level.ERROR, phase = processingPhase)
        return true
    }
    return false
}

/** generates a new file name based on the original report file name: e.g.
 *  OSCake-Report.oscc --> OSCake-Report_curated.oscc
 */
fun extendFilename(reportFile: File, suffix: String): String = "${if (reportFile.parent != null
) reportFile.parent + "/" else ""}${reportFile.nameWithoutExtension}$suffix.${reportFile.extension}"

fun stripRelativePathIndicators(name: String): String {
    if (name.startsWith("./")) return name.substring(2)
    if (name.startsWith(".\\")) return name.substring(2)
    if (name.startsWith(".")) return name.substring(1)
    return name
}
