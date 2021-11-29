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

const val CURATION_DEFAULT_LICENSING = "<DEFAULT_LICENSING>"
const val CURATION_LOGGER = "OSCakeCurator"
const val MERGER_LOGGER = "OSCakeMerger"
const val MERGER_VERSION = "0.1"
const val OSCAKE_MERGER_AUTHOR = "OSCake-Merger"
const val OSCAKE_MERGER_ARCHIVE_TYPE = "zip"

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
   val ALL by lazy { listOf("curator", "merger") }
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
