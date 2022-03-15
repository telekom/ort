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

package org.ossreviewtoolkit.oscake.resolver

import java.util.SortedSet

import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * Data class which gets the infos from an ORT analyzer-results file
 */
internal data class AnalyzerLicenses(
    val declaredLicenses: SortedSet<String>,
    val declaredLicensesProcessed: String,
    val mapped: Map<String, SpdxExpression>
) {
    val mappedLicenses = declaredLicenses.map { mapped[it]?.toString() ?: it.toString() }.toSortedSet()
}
