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
 * The class TextEntry wraps the [startLine] and [endLine] of a license or copyright finding.
 */
internal interface TextEntry {
    /**
     * [startLine] contains the line number where the license text begins.
     */
    var startLine: Int
    /**
     * [endLine] contains the line number where the license text ends.
     */
    var endLine: Int
}
