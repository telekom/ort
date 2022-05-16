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

import org.ossreviewtoolkit.oscake.CURATION_ACTOR
import org.ossreviewtoolkit.oscake.CURATION_AUTHOR
import org.ossreviewtoolkit.oscake.CURATION_FILE_SUFFIX
import org.ossreviewtoolkit.oscake.CURATION_LOGGER
import org.ossreviewtoolkit.oscake.CURATION_VERSION
import org.ossreviewtoolkit.oscake.METADATAMANAGER_ACTOR
import org.ossreviewtoolkit.oscake.METADATAMANAGER_AUTHOR
import org.ossreviewtoolkit.oscake.METADATAMANAGER_FILE_SUFFIX
import org.ossreviewtoolkit.oscake.METADATAMANAGER_LOGGER
import org.ossreviewtoolkit.oscake.METADATAMANAGER_VERSION
import org.ossreviewtoolkit.oscake.RESOLVER_ACTOR
import org.ossreviewtoolkit.oscake.RESOLVER_AUTHOR
import org.ossreviewtoolkit.oscake.RESOLVER_FILE_SUFFIX
import org.ossreviewtoolkit.oscake.RESOLVER_LOGGER
import org.ossreviewtoolkit.oscake.RESOLVER_VERSION
import org.ossreviewtoolkit.oscake.SELECTOR_ACTOR
import org.ossreviewtoolkit.oscake.SELECTOR_AUTHOR
import org.ossreviewtoolkit.oscake.SELECTOR_FILE_SUFFIX
import org.ossreviewtoolkit.oscake.SELECTOR_LOGGER
import org.ossreviewtoolkit.oscake.SELECTOR_VERSION
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.ProcessingPhase

/**
 * [ActionInfo] wraps information about different actions like "curate", etc.
 */
class ActionInfo private constructor(
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
        internal fun metadatamanager(issueLevel: Int) = ActionInfo(
            METADATAMANAGER_LOGGER,
            ProcessingPhase.METADATAMANAGER,
            METADATAMANAGER_AUTHOR,
            METADATAMANAGER_VERSION,
            METADATAMANAGER_ACTOR,
            METADATAMANAGER_FILE_SUFFIX,
            issueLevel
        )
    }
}
