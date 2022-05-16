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
 * [DistributionType] contains a list of possible distribution types; as the type depends on other distribution
 * types, the plus operator is overloaded
 */
enum class DistributionType { DISTRIBUTED, PREINSTALLED, DEV }

// implements the priority rules, if the type is already set
operator fun DistributionType.plus(newType: DistributionType?): DistributionType? {
    if (this == DistributionType.DISTRIBUTED) return DistributionType.DISTRIBUTED
    if (newType == DistributionType.DISTRIBUTED) return DistributionType.DISTRIBUTED
    if (this == DistributionType.PREINSTALLED) return DistributionType.PREINSTALLED
    if (newType == DistributionType.PREINSTALLED) return DistributionType.PREINSTALLED
    if (this == DistributionType.DEV) return DistributionType.DEV
    if (newType == DistributionType.DEV) return DistributionType.DEV
    return newType
}
