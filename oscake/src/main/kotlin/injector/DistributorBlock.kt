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

package org.ossreviewtoolkit.oscake.injector

import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.DistributionType

/**
 * A [DistributorBlock] contains one distributor-action for a specific package
 */
internal data class DistributorBlock(
    /**
     * change distribution from
     */
    val from: DistributionType,
    /**
     * change distribution to
     */
    val to: DistributionType,
)
