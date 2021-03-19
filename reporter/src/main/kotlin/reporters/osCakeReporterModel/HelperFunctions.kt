package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

import org.apache.logging.log4j.Level
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression
import java.io.File
import java.nio.file.FileSystems

const val FOUND_IN_FILE_SCOPE_DECLARED = "[DECLARED]"
const val REUSE_LICENSES_FOLDER = "LICENSES/"
const val CURATION_DEFAULT_LICENSING = "<DEFAULT_LICENSING>"
const val CURATION_LOGGER = "OSCakeCuration"
const val REPORTER_LOGGER = "OSCakeReporter"

internal fun isInstancedLicense(input: ReporterInput, license: String): Boolean =
    input.licenseClassifications.licensesByCategory.getOrDefault("instanced",
        setOf<SpdxSingleLicenseExpression>()).map { it.simpleLicense().toString() }.contains(license)

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
    if(pack.reuseCompliant && rc.endsWith(".license")) {
        val pos = rc.indexOfLast { it == '.' }
        rc = rc.substring(0, pos)
    }
    return rc
}

internal fun handleOSCakeIssues(project: Project, logger: OSCakeLogger) {
    // create testcases!
    //createTestLogs4Reporter(logger)

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
        it.level == Level.WARN || it.level == Level.ERROR }?: false

    // set global hasIssues
    project.hasIssues = hasIssuesGlobal
}

internal fun createTestLogs4Reporter(logger: OSCakeLogger) {
    // logger.log("Testcase #1 no package no fib", Level.ERROR)
    // logger.log("Testcase #3 no package no fib", Level.WARN)
    logger.log("Testcase #5 no package no fib", Level.INFO)

    val id = Identifier("Maven", "de.tdosca.tc06", "tdosca-tc06", "1.0")
    logger.log("Testcase #7 no fib", Level.ERROR, id)
    logger.log("Testcase #8 no fib", Level.WARN, id)
    logger.log("Testcase #9 no fib", Level.INFO, id)

    val fib = FileInfoBlock("/irgendwo/kkk.txt")
    // logger.log("Testcase #10", Level.ERROR, id, fib)
    // logger.log("Testcase #11", Level.WARN, id, fib)
    logger.log("Testcase #12", Level.INFO, id, fib.path)

    val id2 = Identifier("Maven", "joda-time", "joda-time", "2.10.8")
    val fib2 = FileInfoBlock("joda-time/irgendwo/kkk.txt")
    //logger.log("Testcase #13", Level.ERROR, id2, fib2)
    //logger.log("Testcase #14", Level.WARN, id2, fib2)
    logger.log("Testcase #15", Level.INFO, id2, fib2.path)

}
