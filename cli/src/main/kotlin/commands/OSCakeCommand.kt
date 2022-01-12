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
import org.ossreviewtoolkit.oscake.isValidDirectory
import org.ossreviewtoolkit.oscake.isValidFilePathName
import org.ossreviewtoolkit.utils.expandTilde

sealed class OscakeConfig(name: String) : OptionGroup(name)

/**
 * Contains the options for the curator application
 */
class CuratorOptions : OscakeConfig("Options for oscake application: curator") {
    val osccFile by option(
        "--oscc-file", "-i",
        help = "An oscc file produced by an OSCake-Reporter."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    val outputDir by option(
        "--output-dir", "-o",
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
class MergerOptions : OscakeConfig("Options for oscake application: merger") {
    internal val inputDir by option("--inputDirectory", "-id", help = "The path to a folder containing oscc " +
            "files and their corresponding archives. May also consist of subdirectories.")
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .required()

    internal val outputDirArg by option("--outputDirectory", "-od", help = "The path to the output folder.")
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = true, mustBeReadable = true)

    internal val cid by option("--cid", "-c", help = "Id of the Compliance Artifact Collection - IVY format preferred.")
        .required()

    internal val outputFileArg by option("--outputFile", "-of", help = "Name of the output file. When -o is " +
            "also specified, the path to the outputFile is stripped to its name.")
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)

    internal lateinit var outputFile: File

    internal fun resolveArgs() {
        require(!(outputDirArg == null && outputFileArg == null)) { "Either <outputDirectory> and/or <outputFile> " +
                "must be specified!" }

        outputFile = if (outputFileArg == null) {
            val fileName = cid.replace(":", ".")
            require(isValidFilePathName(fileName)) { "$fileName - output file name not valid - it may contain + " +
                    "special characters!" }

            File("$fileName.oscc")
        } else {
            require(isValidFilePathName(outputFileArg!!.name)) { "$outputFileArg - output file name not " +
                    "valid - it may contain special characters!" }
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
        "--osccFile", "-if",
        help = "An oscc file produced by an OSCake-Reporter or OSCake-Curator."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
}

class OSCakeCommand : CliktCommand(name = "oscake", help = "Initiate oscake applications: curator, merger, etc.") {
    private val allOSCakeApplications = OSCakeApplication.ALL.associateBy { it.toString() }
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
    ).required()

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()

    /**
     * runs the configured oscake application, passed by parameter
     */
    override fun run() {
        val config = globalOptionsForSubcommands.config
        val fields2hide = OscakeConfig::class.memberProperties.map { it.name }

        when (val it = oscakeApp) {
            is CuratorOptions -> {
                require(isValidDirectory(config.oscake.curator?.fileStore)) {
                    "Directory for \"config.oscake.curator.fileStore\" is not set correctly in ort.conf"
                }
                require(isValidDirectory(config.oscake.curator?.directory)) {
                    "Directory for \"config.oscake.curator.directory\" is not set correctly in ort.conf"
                }
                OSCakeCurator(config.oscake, getCuratorCommandLineParams(it, fields2hide)).execute()
            }
            is DeduplicatorOptions -> {
                OSCakeDeduplicator(config.oscake, it.osccFile,
                    getDeduplicatorCommandLineParams(it, fields2hide)).execute()
            }
            is MergerOptions -> {
                it.resolveArgs()
                OSCakeMerger(it.cid, it.inputDir, it.outputFile, getMergerCommandLineParams(it, fields2hide)).execute()
            }
        }
    }

    private fun getCuratorCommandLineParams(it: CuratorOptions, fields2hide: List<String>): Map<String, String> {
        val commandLineParams = mutableMapOf<String, String>()
        CuratorOptions::class.memberProperties.filter { !fields2hide.contains(it.name) }.forEach { member ->
            commandLineParams[member.name] =
                if (member.get(it) is File) getRelativeFileName(member.get(it) as File) else member.get(it).toString()
        }
        return commandLineParams
    }

    private fun getDeduplicatorCommandLineParams(it: DeduplicatorOptions, fields2hide: List<String>):
            Map<String, String> {
        val commandLineParams = mutableMapOf<String, String>()
        DeduplicatorOptions::class.memberProperties.filter { !fields2hide.contains(it.name) }.forEach { member ->
            commandLineParams[member.name] =
                if (member.get(it) is File) getRelativeFileName(member.get(it) as File) else member.get(it).toString()
        }
        return commandLineParams
    }

    private fun getMergerCommandLineParams(it: MergerOptions, fields2hide: List<String>): Map<String, String> {
        val commandLineParams = mutableMapOf<String, String>()
        MergerOptions::class.memberProperties.filter { !fields2hide.contains(it.name) }.forEach { member ->
            commandLineParams[member.name] =
                if (member.get(it) is File) getRelativeFileName(member.get(it) as File) else member.get(it).toString()
        }
        return commandLineParams
    }

    private fun getRelativeFileName(file: File): String =
        file.relativeTo(Paths.get("").toAbsolutePath().toFile()).toString()
}
