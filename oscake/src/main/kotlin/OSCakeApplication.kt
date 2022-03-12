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
import java.io.IOException

import kotlin.reflect.full.memberProperties

import org.ossreviewtoolkit.model.config.OSCakeConfiguration
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ConfigBlockInfo

const val CURATION_DEFAULT_LICENSING = "<DEFAULT_LICENSING>"
const val CURATION_LOGGER = "OSCakeCurator"
const val CURATION_FILE_SUFFIX = "_curated"
const val CURATION_AUTHOR = "OSCake-Curator"
const val CURATION_VERSION = "0.1"
const val CURATION_ACTOR = "Curator"

const val MERGER_LOGGER = "OSCakeMerger"
const val MERGER_VERSION = "0.1"
const val MERGER_AUTHOR = "OSCake-Merger"

const val DEDUPLICATION_LOGGER = "OSCakeDeduplicator"
const val DEDUPLICATION_FILE_SUFFIX = "_dedup"
const val DEDUPLICATION_AUTHOR = "OSCake-Deduplicator"
const val DEDUPLICATION_VERSION = "0.1"

const val VALIDATOR_LOGGER = "OSCakeValidator"

const val RESOLVER_LOGGER = "OSCakeResolver"
const val RESOLVER_FILE_SUFFIX = "_resolved"
const val RESOLVER_AUTHOR = "OSCake-Resolver"
const val RESOLVER_VERSION = "0.1"
const val RESOLVER_ACTOR = "Resolver"

const val SELECTOR_LOGGER = "OSCakeSelector"
const val SELECTOR_FILE_SUFFIX = "_selected"
const val SELECTOR_AUTHOR = "OSCake-Selector"
const val SELECTOR_VERSION = "0.1"
const val SELECTOR_ACTOR = "Selector"
const val SELECTOR_GLOBAL_INDICATOR = "[GLOBAL]"

/**
 * The [packageModifierMap] is a Hashmap which defines the allowed packageModifier (=key) and their associated
 * modifiers - the first set contains modifiers for licenses, second set for copyrights
 * Important: the sequence of items in the sets defines also the sequence of curations
 * e.g.: for packageModifier: "update" the sequence of curations is "delete-all", than "delete" and finally "insert"
 */
val packageModifierMap = hashMapOf("delete" to listOf(setOf(), setOf()),
    "insert" to listOf(setOf("insert"), setOf("insert")),
    "update" to listOf(setOf("delete", "insert", "update"), setOf("delete-all", "delete", "insert"))
)

/**
 * [orderLicenseByModifier] defines the sort order of curations for licenses.
 */
val orderLicenseByModifier = packageModifierMap.map { it.key to packageModifierMap[it.key]?.get(0)?.
withIndex()?.associate { a -> a.value to a.index } }.toMap()

/**
 * [orderCopyrightByModifier] defines the sort order of curations for copyrights.
 */
val orderCopyrightByModifier = packageModifierMap.map { it.key to packageModifierMap[it.key]?.get(1)?.
withIndex()?.associate { a -> a.value to a.index } }.toMap()

/**
 * checks if the value of the optionName in map is a valid file
 */
fun isValidDirectory(dirName: String?): Boolean =
    if (dirName != null) File(dirName).exists() && File(dirName).isDirectory else false

object OSCakeApplication {
   val ALL by lazy { listOf("curator", "merger", "deduplicator", "validator", "resolver", "selector") }
}

/**
 * Checks if the [path] contains invalid characters for file names
 */
@Suppress("SwallowedException")
fun isValidFilePathName(path: String): Boolean =
    try {
        File(path).canonicalPath
        true
    } catch (e: IOException) {
        false
    }

/**
 * Creates config information for oscc-config-section (deduplicator, curator); every entry in config file is
 * transferred to output format. If some parameters are added to the config, the entries will be automatically
 * transferred
 */

fun addParamsToConfig(config: OSCakeConfiguration, commandLineParams: Map<String, String>,
                      clazz: Any): ConfigBlockInfo? {
    OSCakeConfiguration::class.memberProperties.forEach { member ->
        val paramMap = mutableMapOf<String, String>()
        when (val v = member.get(config)) {
            is org.ossreviewtoolkit.model.config.OSCakeCurator -> {
                if (clazz is OSCakeCurator) {
                    org.ossreviewtoolkit.model.config.OSCakeCurator::class.memberProperties.forEach { member2 ->
                        paramMap[member2.name] = member2.get(v).toString()
                    }
                    return ConfigBlockInfo(commandLineParams, paramMap)
                }
            }
            is org.ossreviewtoolkit.model.config.OSCakeResolver -> {
                if (clazz is OSCakeResolver) {
                    org.ossreviewtoolkit.model.config.OSCakeResolver::class.memberProperties.forEach { member2 ->
                        paramMap[member2.name] = member2.get(v).toString()
                    }
                    return ConfigBlockInfo(commandLineParams, paramMap)
                }
            }
            is org.ossreviewtoolkit.model.config.OSCakeSelector -> {
                if (clazz is OSCakeSelector) {
                    org.ossreviewtoolkit.model.config.OSCakeSelector::class.memberProperties.forEach { member2 ->
                        paramMap[member2.name] = member2.get(v).toString()
                    }
                    return ConfigBlockInfo(commandLineParams, paramMap)
                }
            }
            is org.ossreviewtoolkit.model.config.OSCakeDeduplicator -> {
                if (clazz is OSCakeDeduplicator) {
                    org.ossreviewtoolkit.model.config.OSCakeDeduplicator::class.memberProperties.forEach { member2 ->
                        paramMap[member2.name] = member2.get(v).toString()
                    }
                    return ConfigBlockInfo(commandLineParams, paramMap)
                }
            }
        }
    }
    return null
}
