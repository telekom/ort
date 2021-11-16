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

package org.ossreviewtoolkit.oscake.curator

/**
 * A class defining a curation for a copyright.
 */
internal data class CurationFileCopyrightItem(
    /**
     * The [modifier] defines the application of the curation: delete, insert or delete-all.
     */
    val modifier: String,
    /**
     * The optional [reason] is a description of the necessity of the curation.
     */
    val reason: String?,
    /**
     * The [copyright] is used to identify the specific copyright text; it may contain a string,
     * a placeholder "*" or null. The [copyright] may also contain wildcards, like: "*" or "?".
     */
    val copyright: String?
)
