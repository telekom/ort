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

package org.ossreviewtoolkit.cli.commands

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import java.io.File
import java.nio.file.Paths

import kotlin.reflect.full.memberProperties

import org.ossreviewtoolkit.cli.GlobalOptions
import org.ossreviewtoolkit.oscake.OSCakeApplication
import org.ossreviewtoolkit.oscake.OSCakeCurator
import org.ossreviewtoolkit.oscake.OSCakeDeduplicator
import org.ossreviewtoolkit.oscake.OSCakeMerger
import org.ossreviewtoolkit.oscake.OSCakeMetaDataManager
import org.ossreviewtoolkit.oscake.OSCakeResolver
import org.ossreviewtoolkit.oscake.OSCakeSelector
import org.ossreviewtoolkit.oscake.OSCakeValidator
import org.ossreviewtoolkit.oscake.isValidDirectory
import org.ossreviewtoolkit.oscake.isValidFilePathName
import org.ossreviewtoolkit.utils.common.expandTilde

sealed class OscakeConfig(name: String) : OptionGroup(name)

/**
 * Contains the options for the curator application
 */
class ValidatorOptions : OscakeConfig("Options for oscake application: validator") {
    val oscc1 by option(
        "--valInp1", "-vI1",
        help = "An oscc file produced by an OSCake-Reporter."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
    val oscc2 by option(
        "--valInp2", "-vI2",
        help = "An oscc file produced by an OSCake-Reporter."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
}
/**
 * Contains the options for the resolver application
 */
@Suppress("unused")
class ResolverOptions : OscakeConfig("Options for oscake application: resolver") {
    val osccFile by option(
        "--resInp", "-rI",
        help = "An oscc file produced by an OSCake-Reporter."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    val outputDir by option(
        "--resOut-Dir", "-rO",
        help = "The directory to write the resolved oscc file."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    val analyzerFile by option(
        "--analyzer-File", "-rA",
        help = "An analyzer-result.yml file produced by ORT (base of the OSCake-Reporter stored in oscc-file)."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
}

/**
 * Contains the options for the resolver application
 */
@Suppress("unused")
class SelectorOptions : OscakeConfig("Options for oscake application: selector") {
    val osccFile by option(
        "--selInp", "-sI",
        help = "An oscc file produced by an OSCake-Reporter or by an OSCake-Resolver."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    val outputDir by option(
        "--selOut-Dir", "-sO",
        help = "The directory to write the generated oscc file."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()
}

/**
 * Contains the options for the metadata-manager application
 */
@Suppress("unused")
class MetaDataManagerOptions : OscakeConfig("Options for oscake application: metadata-manager") {
    val osccFile by option(
        "--injInp", "-iI",
        help = "An oscc file produced by an OSCake-Reporter or by an OSCake-Resolver."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    val outputDir by option(
        "--injOut-Dir", "-iO",
        help = "The directory to write the generated oscc file."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()
    val ignoreFromChecks by option(
        "--ignoreFromChecks",
        help = "Ignore the semantic checks for \"from\"-tag"
    ).flag()
}

/**
 * Contains the options for the curator application
 */
@Suppress("unused")
class CuratorOptions : OscakeConfig("Options for oscake application: curator") {
    val osccFile by option(
        "--curInp", "-cI",
        help = "An oscc file produced by an OSCake-Reporter."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    val outputDir by option(
        "--curOut-Dir", "-cO",
        help = "The directory to write the curated oscc file."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    val ignoreRootWarnings by option("--ignoreRootWarnings", help = "Ignore Root-Level WARNINGS").flag()
}

/**
 * Contains the options for the merger application
 */
@Suppress("MemberVisibilityCanBePrivate")
class MergerOptions : OscakeConfig("Options for oscake application: merger") {
    internal val inputDir by option(
        "--merInp-Dir",
        "-mI",
        help = "The path to a folder containing oscc files and their corresponding archives. May also " +
                "consist of subdirectories."
    ).file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    internal val outputDirArg by option("--merOut-Dir", "-mO", help = "The path to the output folder.")
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = true, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    internal val cid by option("--cid", help = "Id of the Compliance Artifact Collection - IVY format preferred.")
        .required()

    internal val outputFileArg by option(
        "--merOut-File",
        "-mF",
        help = "Name of the output file. When -mO is also specified, the path to the outputFile is stripped " +
                "to its name."
    ).file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }

    internal lateinit var outputFile: File

    internal fun resolveArgs() {
        require(!(outputDirArg == null && outputFileArg == null)) {
            "Either <outputDirectory> and/or <outputFile> must be specified!"
        }

        outputFile = if (outputFileArg == null) {
            val fileName = cid.replace(":", ".")
            require(isValidFilePathName(fileName)) {
                "$fileName - output file name not valid - it may contain + special characters!"
            }

            File("$fileName.oscc")
        } else {
            require(isValidFilePathName(outputFileArg!!.name)) {
                "$outputFileArg - output file name not valid - it may contain special characters!"
            }
            outputFileArg as File
        }

        // if output directory is given, strip the output files to their file names
        if (outputDirArg != null) {
            outputFile = outputDirArg!!.resolve(outputFile.name)
        }
    }
}

/**
 * Contains the options for the deduplicator application
 */
class DeduplicatorOptions : OscakeConfig("Options for oscake application: deduplicator") {
    val osccFile by option(
        "--dedInp", "-dI",
        help = "An oscc file produced by an OSCake-Reporter or OSCake-Curator."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
}

class OSCakeCommand : CliktCommand(name = "oscake", help = "Initiate oscake applications: curator, merger, etc.") {
    private val allOSCakeApplications = OSCakeApplication.ALL.associateBy { it }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)

    private val oscakeApp by option(
        "--app", "-a",
        help = "The app to use, any of ${allOSCakeApplications.keys}."
    ).convert { name ->
        allOSCakeApplications[name]
            ?: throw BadParameterValue(
                "OSCake '$name' is not one of ${allOSCakeApplications.keys}."
            )
    }.groupChoice(
        "curator" to CuratorOptions(),
        "merger" to MergerOptions(),
        "deduplicator" to DeduplicatorOptions(),
        "validator" to ValidatorOptions(),
        "resolver" to ResolverOptions(),
        "selector" to SelectorOptions(),
        "metadata-manager" to MetaDataManagerOptions(),
    ).required()

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()

    /**
     * runs the configured oscake application, passed by parameter
     */
    override fun run() {
        val config = globalOptionsForSubcommands.config
        val fieldsList = OscakeConfig::class.memberProperties.map { it.name }

        when (val it = oscakeApp) {
            is CuratorOptions -> {
                require(isValidDirectory(config.oscake.curator?.fileStore)) {
                    "Directory for \"config.oscake.curator.fileStore\" is not set correctly in ort.conf"
                }
                require(isValidDirectory(config.oscake.curator?.directory)) {
                    "Directory for \"config.oscake.curator.directory\" is not set correctly in ort.conf"
                }
                OSCakeCurator(config.oscake, getCommandLineParams(it, fieldsList)).execute()
            }
            is DeduplicatorOptions -> {
                OSCakeDeduplicator(config.oscake, it.osccFile, getCommandLineParams(it, fieldsList)).execute()
            }
            is MergerOptions -> {
                it.resolveArgs()
                OSCakeMerger(
                    config.oscake,
                    it.cid,
                    it.inputDir,
                    it.outputFile,
                    getCommandLineParams(it, fieldsList)
                ).execute()
            }
            is ValidatorOptions -> {
                OSCakeValidator(it.oscc1, it.oscc2).execute()
            }
            is ResolverOptions -> {
                require(isValidDirectory(config.oscake.resolver?.directory)) {
                    "Directory for \"config.oscake.resolver.directory\" is not set correctly in ort.conf"
                }
                OSCakeResolver(config.oscake, getCommandLineParams(it, fieldsList)).execute()
            }
            is SelectorOptions -> {
                require(isValidDirectory(config.oscake.selector?.directory)) {
                    "Directory for \"config.oscake.selector.directory\" is not set correctly in ort.conf"
                }
                OSCakeSelector(config.oscake, getCommandLineParams(it, fieldsList)).execute()
            }
            is MetaDataManagerOptions -> {
                if (config.oscake.metadatamanager?.distribution?.enabled == true)
                    require(isValidDirectory(config.oscake.metadatamanager?.distribution?.directory)) {
                    "Directory for \"config.oscake.metadatamanager.distribution\" is not set correctly in ort.conf"
                }
                if (config.oscake.metadatamanager?.packageType?.enabled == true)
                    require(isValidDirectory(config.oscake.metadatamanager?.packageType?.directory)) {
                    "Directory for \"config.oscake.metadatamanager.packageType\" is not set correctly in ort.conf"
                }
                OSCakeMetaDataManager(config.oscake, getCommandLineParams(it, fieldsList)).execute()
            }
        }
    }

    /**
     * returns a map of set options and its values
     */
    private inline fun <reified T : OscakeConfig> getCommandLineParams(clazz: T, fieldsList: List<String>):
            Map<String, String> {
        val commandLineParams = mutableMapOf<String, String>()
        T::class.memberProperties.filter { !fieldsList.contains(it.name) }.forEach { member ->
            commandLineParams[member.name] = if (member.get(clazz) is File) {
                getRelativeFileName(member.get(clazz) as File)
            } else member.get(clazz).toString()
        }
        return commandLineParams
    }

    private fun getRelativeFileName(file: File): String =
        file.relativeTo(Paths.get("").toAbsolutePath().toFile()).toString()
}
