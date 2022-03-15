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

package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel

/**
 * The class [CompoundLicense] represent a license string consisting of two SPFX license identifier
 * combined with "OR"
 */
data class CompoundLicense(val expression: String?) {
    var isCompound = false
    var left: String = expression ?: ""
    var right: String = ""

    init {
        val arr = expression?.split(" ")
        expression?.takeIf { expression.contains(" OR ") && arr?.size == 3 }?.let {
            left = arr?.get(0) ?: expression
            right = arr?.get(2) ?: ""
            isCompound = true
        }
    }

    override fun toString(): String = expression ?: ""

    override fun equals(other: Any?): Boolean = other is CompoundLicense &&
            ((other.left == left && other.right == right) || (other.left == right && other.right == left))

    // automatically generated because custom implementation of "equals"
    override fun hashCode(): Int {
        var result = expression?.hashCode() ?: 0
        result = 31 * result + isCompound.hashCode()
        result = 31 * result + left.hashCode()
        result = 31 * result + right.hashCode()
        return result
    }
}
