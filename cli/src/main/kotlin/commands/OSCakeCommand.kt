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
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.cli.GlobalOptions
import org.ossreviewtoolkit.cli.utils.outputGroup
import org.ossreviewtoolkit.oscake.OSCakeApplication
import org.ossreviewtoolkit.oscake.OSCakeCurator
import org.ossreviewtoolkit.utils.expandTilde

class OSCakeCommand : CliktCommand(name = "oscake", help = "Check dependencies for security vulnerabilities.") {
    private val allOSCakeApplications = OSCakeApplication.ALL.associateBy { it.toString() }.toSortedMap(String.CASE_INSENSITIVE_ORDER)

    private val oscakeApp by option(
        "--app", "-a",
        help = "The app to use, any of ${allOSCakeApplications.keys}."
    ).convert { name ->
        allOSCakeApplications[name]
            ?: throw BadParameterValue(
                "OSCake '$name' is not one of ${allOSCakeApplications.keys}."
            )
    }.required()

    private val osccFile by option(
        "--oscc-file", "-i",
        help = "An oscc file produced by an OSCake-Reporter."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

   private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the curated oscc file."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()
        .outputGroup()

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()


    override fun run() {
        val config = globalOptionsForSubcommands.config
        when (oscakeApp) {
            "curator" -> OSCakeCurator(config.oscake, osccFile, outputDir).execute()
        }
    }
}
