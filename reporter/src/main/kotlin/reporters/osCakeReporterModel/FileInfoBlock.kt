/*
 * Copyright (C) 2021 Deutsche Telekom AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
 * The class FileInfoBlock holds information gathered from the native scan result files and represents it by
 * the collection [licenseTextEntries] and [copyrightTextEntries] for each analyzed file, defined in [path]
 */
internal data class FileInfoBlock(val path: String) {
    /**
     * [licenseTextEntries] represents a list of licenses and its properties bundled in the class [LicenseTextEntry]
     */
    val licenseTextEntries = mutableListOf<LicenseTextEntry> ()
    /**
     * [copyrightTextEntries] represents a list of copyrights ([CopyrightTextEntry])
     */
    val copyrightTextEntries = mutableListOf<CopyrightTextEntry>()
}
