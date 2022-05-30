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

@Suppress("EqualsWithHashCodeExist")
/**
 * Custom filter for writing issues into the output file (for jackson)
 */
class IssuesFilterCustom {
    override fun equals(other: Any?): Boolean {
        // do not serialize when the lists are empty
        if (other is IssueList) {
            if (other.errors.isEmpty() && other.infos.isEmpty() && other.warnings.isEmpty()) return true
        }
        if (other == null) return true

        return false
    }
}
