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
 * The class [CopyrightTextEntry] contains information about the location and the corresponding text of the
 * copyright statement.
 */
internal data class CopyrightTextEntry(
    /**
     * [startLine] of the textblock which contains the copyright text in the source file.
     */
    override var startLine: Int = -1,
    /**
     * [endLine] of the textblock which contains the copyright text in the source file.
     */
    override var endLine: Int = -1,
    /**
     * [matchedText] contains the copyright text provided by the scanner output.
     */
    var matchedText: String? = null
) : TextEntry
