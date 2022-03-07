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

import org.ossreviewtoolkit.oscake.*
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.ProcessingPhase

/**
 * [ActionInfo] wraps information about different actions like "curate", etc.
 */
class ActionInfo private constructor (
        val loggerName: String,
        val phase: ProcessingPhase,
        val author: String,
        val release: String,
        val actor: String,
        val suffix: String,
        val issueLevel: Int
) {
    companion object {
        internal fun curator(issueLevel: Int) = ActionInfo(
            CURATION_LOGGER,
            ProcessingPhase.CURATION,
            CURATION_AUTHOR,
            CURATION_VERSION,
            CURATION_ACTOR,
            CURATION_FILE_SUFFIX,
            issueLevel
        )

        internal fun resolver(issueLevel: Int) = ActionInfo(
            RESOLVER_LOGGER,
            ProcessingPhase.RESOLVING,
            RESOLVER_AUTHOR,
            RESOLVER_VERSION,
            RESOLVER_ACTOR,
            RESOLVER_FILE_SUFFIX,
            issueLevel
        )
        internal fun selector(issueLevel: Int) = ActionInfo(
            SELECTOR_LOGGER,
            ProcessingPhase.SELECTION,
            SELECTOR_AUTHOR,
            SELECTOR_VERSION,
            SELECTOR_ACTOR,
            SELECTOR_FILE_SUFFIX,
            issueLevel
        )
    }
}
