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

package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils

/**
 * The class [CompoundOrLicense] represent a license string consisting of two or more SPDX license identifier
 * combined with "OR"
 */
data class CompoundOrLicense(val expression: String?) {
    var isCompound = false
    val licenseList: MutableList<String> = mutableListOf()

    init {
        expression?.split(" OR ")?.let { licenseList.addAll(it) }
        if (licenseList.size > 1) isCompound = true
    }

    override fun toString(): String = expression ?: ""

    override fun equals(other: Any?): Boolean =
        other is CompoundOrLicense && licenseList.toSortedSet() == other.licenseList.toSortedSet()
}
