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

package org.ossreviewtoolkit.oscake.common

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.oscake.GLOBAL_INDICATOR
import org.ossreviewtoolkit.oscake.curator.CurationPackage
import org.ossreviewtoolkit.oscake.injector.DistributorPackage
import org.ossreviewtoolkit.oscake.resolver.ResolverPackage
import org.ossreviewtoolkit.oscake.selector.SelectorPackage
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.OSCakeLoggerManager
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase

/**
 * The [ActionProvider] walks through the given "directory" and collects the identified
 * [ActionPackage]s.
 */
abstract class ActionProvider internal constructor(
    directory: File, fileStore: File?, loggerName: String,
    clazz: Any, processingPhase: ProcessingPhase) {

    internal val actions = mutableListOf<ActionPackage>()
    /**
     * The [logger] is only initialized, if there is something to log.
     */
    internal val logger: OSCakeLogger by lazy { OSCakeLoggerManager.logger(loggerName) }
    /**
     * Stores the actual processing phase for logger reasons
     */
    private val phase = processingPhase
    /**
     * [errors] stores if there were some errors during the processing
     */
    companion object {
        var errors = false
    }
    /**
     * The init method walks through the folder hierarchy - starting at "directory" - and creates a list
     * of actions.
     */
    init {
        if (directory.isDirectory) {
            directory.walkTopDown().filter { it.isFile && it.extension == "yml" }.forEach { file ->
                try {
                    when (clazz) {
                        ResolverPackage::class -> file.readValue<List<ResolverPackage>>()
                        CurationPackage::class -> file.readValue<List<CurationPackage>>()
                        SelectorPackage::class -> file.readValue<List<SelectorPackage>>()
                        DistributorPackage::class -> file.readValue<List<DistributorPackage>>()
                        else -> { listOf() }
                    }.forEach {
                        it.belongsToFile = file
                        if (checkSemantics(it, file.name, fileStore)) actions.add(it)
                    }
                } catch (e: IOException) {
                    logger.log("Error while processing file: ${file.absoluteFile}! - Action not applied! ${e.message}",
                        Level.ERROR, phase = phase
                    )
                    errors = true
                }
            }
        }
    }

    /**
     * [checkSemantics] has to be implemented by child classes in order to validate the actions
     */
    internal abstract fun checkSemantics(item: ActionPackage, fileName: String, fileStore: File?): Boolean

    /**
     * Returns the [ActionPackage] which is applicable for a specific package ID or null if there is none or
     * more than one.
     */
    internal fun getActionFor(pkgId: Identifier, globalAllowed: Boolean = false): ActionPackage? {
        actions.filter { it.isApplicable(pkgId) }.apply {
            if (size > 1) logger.log("Error: more than one action was found for" +
                    " package: $pkgId - don't know which one to take!", Level.ERROR, pkgId,
                phase = phase
            )
            // only needed for selector-application with id [GLOBAL]
            if (globalAllowed && size == 0 && actions.any { it.id.type == GLOBAL_INDICATOR })
                return actions.first { it.id.type == GLOBAL_INDICATOR }
            if (size != 1) return null
            return first()
        }
    }
}
