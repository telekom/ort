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

package org.ossreviewtoolkit.oscake

import java.io.File

import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*

/**
 * The [OSCakeValidator] provides a mechanism to curate issues (WARNINGS & ERRORS) in an *.oscc file. Additionally,
 * ComplianceArtifactPackages can be added and/or deleted.
 */
class OSCakeValidator(private val oscc1: File, private val oscc2: File) {
    private val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(VALIDATOR_LOGGER) }

    /**
     * Generates the json from file and starts the curation process
     */
    fun execute() {
        // still open: defaultCopyrights, dirLicensings, dirCopyrights

        val project1 = Project.osccToModel(oscc1, logger, ProcessingPhase.VALIDATING)
        val project2 = Project.osccToModel(oscc2, logger, ProcessingPhase.VALIDATING)

        if (!packageComparison(project1, project2)) return else println("Package comparison is OK!")

        project1.packs.forEach { pack ->
            compareFileLicensings(pack, project2.packs.first { it.id == pack.id })
        }

        project1.packs.forEach { pack ->
            compareLicenseTextInArchive(pack, project2.packs.first { it.id == pack.id })
        }

        project1.packs.forEach { pack ->
            compareFileContentInArchive(pack, project2.packs.first { it.id == pack.id })
        }

        project1.packs.forEach { pack ->
            pack.fileLicensings.forEach { fileLicensing1 ->
                var fileLicensing2: FileLicensing? = null
                project2.packs.filter { it.id == pack.id }.forEach { pack2 ->
                    fileLicensing2 = pack2.fileLicensings.firstOrNull { it.scope == fileLicensing1.scope }
                }
                if (fileLicensing2 != null) {
                    compareFileLicensingDetail(fileLicensing1, fileLicensing2!!)
                }
            }
        }
        project1.packs.forEach { pack ->
            val defaultLicenses2 = project2.packs.firstOrNull { it.id == pack.id }?.defaultLicensings
            if (defaultLicenses2 != null)
                compareDefaultLicenses(pack.defaultLicensings, defaultLicenses2)
            else
                println("no default-licenses found in oscc2 ${pack.id.toCoordinates()}!")
        }
    }

    private fun compareDefaultLicenses(df1: MutableList<DefaultLicense>, df2: MutableList<DefaultLicense>): Boolean {
        val d1 = df1.map { it.path + "!" + it.license + "!" + it.licenseTextInArchive }.toSet()
        val d2 = df2.map { it.path + "!" + it.license + "!" + it.licenseTextInArchive }.toSet()
        val dif1 = d1.minus(d2)
        val dif2 = d2.minus(d1)
        if (dif1.isNotEmpty()) {
            println("=====>>>>> The following default licenses references are missing in oscc2: Filescope: $d1")
            dif1.forEach { println(it) }
        }
        if (dif2.isNotEmpty()) {
            println("=====>>>>> The following licenses references are missing in oscc1:  Filescope: $d2")
            dif2.forEach { println(it) }
        }
        return true
    }

    private fun compareFileLicensingDetail(fL1: FileLicensing, fL2: FileLicensing): Boolean {
        if (fL1.fileContentInArchive != fL2.fileContentInArchive) {
            println(
                "Filescope: ${fL1.scope} fileContentInArchive are different: [${fL1.fileContentInArchive}] " +
                    "<--> [${fL2.fileContentInArchive}]"
            )
            return false
        }
        if (fL1.licenses.size != fL2.licenses.size)
            println("=====>>>>> Licenses are missing maybe <null> in ${fL1.scope}")
        val f1 = fL1.licenses.mapNotNull { it.license }.toSet()
        val f2 = fL2.licenses.mapNotNull { it.license }.toSet()
        val dif1 = f1.minus(f2)
        val dif2 = f2.minus(f1)
        if (dif1.isNotEmpty()) {
            println("=====>>>>> The following licenses references are missing in oscc2: Filescope: ${fL1.scope}")
            dif1.forEach { println(it) }
        }
        if (dif2.isNotEmpty()) {
            println("=====>>>>> The following licenses references are missing in oscc1:  Filescope: ${fL2.scope}")
            dif2.forEach { println(it) }
        }

        if (fL1.copyrights.size != fL2.copyrights.size)
            println("=====>>>>> The copyrights are missing maybe <null> in ${fL1.scope}")
        val c1 = fL1.copyrights.map { it.copyright }.toSet()
        val c2 = fL2.copyrights.map { it.copyright }.toSet()
        val cif1 = c1.minus(c2)
        val cif2 = c2.minus(c1)
        if (cif1.isNotEmpty()) {
            println("=====>>>>> The following copyrights are missing in oscc2: Filescope: ${fL1.scope}")
            cif1.forEach { println(it) }
        }
        if (cif2.isNotEmpty()) {
            println("=====>>>>> The following copyrights are missing in oscc1:  Filescope: ${fL2.scope}")
            cif2.forEach { println(it) }
        }
        return true
    }

    private fun compareFileContentInArchive(pack1: Pack, pack2: Pack): Boolean {
        println("Analyzing fileContentInArchive: ${pack1.id}")
        var rc = true
        val fL1 = pack1.fileLicensings.mapNotNull { it.fileContentInArchive }.toSet()
        val fL2 = pack2.fileLicensings.mapNotNull { it.fileContentInArchive }.toSet()
        val dif1 = fL1.minus(fL2)
        val dif2 = fL2.minus(fL1)
        if (dif1.isNotEmpty()) {
            println("=====>>>>> The following fileContentInArchive references are missing in oscc2: ")
            dif1.forEach { println(it) }
            rc = false
        }
        if (dif2.isNotEmpty()) {
            println("=====>>>>> The following fileContentInArchive references are missing in oscc1: ")
            dif2.forEach { println(it) }
            rc = false
        }
        if (rc) println("${fL1.size} fileContentInArchive references compared!")
        return rc
    }

    private fun compareLicenseTextInArchive(pack1: Pack, pack2: Pack): Boolean {
        println("Analyzing LicenseTextInArchive: ${pack1.id}")
        var rc = true
        var ltia1: List<String?> = emptyList()
        var ltia2: List<String?> = emptyList()
        pack1.fileLicensings.forEach { fileLicensing ->
            ltia1 = fileLicensing.licenses.mapNotNull { it.licenseTextInArchive }.toList()
        }
        pack2.fileLicensings.forEach { fileLicensing ->
            ltia2 = fileLicensing.licenses.mapNotNull { it.licenseTextInArchive }.toList()
        }

        val dif1 = ltia1.minus(ltia2.toSet())
        val dif2 = ltia2.minus(ltia1.toSet())
        if (dif1.isNotEmpty()) {
            println("=====>>>>> The following LicenseTextInArchive are missing in oscc2: ")
            dif1.forEach { println(it.toString()) }
            rc = false
        }
        if (dif2.isNotEmpty()) {
            println("=====>>>>> The following LicenseTextInArchive are missing in oscc1: ")
            dif2.forEach { println(it.toString()) }
            rc = false
        }
        if (rc) println("${ltia1.size} LicenseTextInArchive references compared!")
        return rc
    }

    private fun compareFileLicensings(pack1: Pack, pack2: Pack): Boolean {
        println("Analyzing FileLicensings: ${pack1.id}")
        var rc = true
        val fL1 = pack1.fileLicensings.map { it.scope }.toSet()
        val fL2 = pack2.fileLicensings.map { it.scope }.toSet()
        val dif1 = fL1.minus(fL2)
        val dif2 = fL2.minus(fL1)
        if (dif1.isNotEmpty()) {
            println("=====>>>>> The following fileLicensings are missing in oscc2: ")
            dif1.forEach { println(it) }
            rc = false
        }
        if (dif2.isNotEmpty()) {
            println("=====>>>>> The following fileLicensings are missing in oscc1: ")
            dif2.forEach { println(it) }
            rc = false
        }
        if (rc) println("${fL1.size} fileLicensings compared!")
        return rc
    }

    private fun packageComparison(project1: Project, project2: Project): Boolean {
        val packs1 = project1.packs.map { it.id }.toSet()
        val packs2 = project2.packs.map { it.id }.toSet()
        if (packs1.size >= packs2.size) {
            packs1.minus(packs2).forEach { println("Package: ${it.toCoordinates()} is missing in ${oscc2.name}") }
        }
        if (packs2.size > packs1.size) {
            packs2.minus(packs1).forEach { println("Package: ${it.toCoordinates()} is missing in ${oscc1.name}") }
        }
        return project1.packs.size == project2.packs.size
    }
}
