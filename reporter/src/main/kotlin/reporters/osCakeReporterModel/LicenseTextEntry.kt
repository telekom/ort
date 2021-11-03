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
 * The class LicenseTextEntry represents a license information, found in the scanner files. Either [isLicenseNotice]
 * or [isLicenseText] must be set to true. The property [isInstancedLicense] depends on the category of the license,
 * which is found in the file "license-classifications.yml"
 */
internal data class LicenseTextEntry(
    /**
     * [startLine] contains the starting line of the license text in the source file.
     */
    override var startLine: Int = -1,
    /**
     * [startLine] contains the last line of the license text in the source file.
     */
    override var endLine: Int = -1,
    /**
     * [license] is the name of the license.
     */
    var license: String? = null,
    /**
     * If the scanner categorized the license finding as license text, [isLicenseText] is set to true.
     */
    var isLicenseText: Boolean = false,
    var isInstancedLicense: Boolean = false,
    /**
     * If the scanner identified a license as reference, notice, etc. and not as text [isLicenseNotice] is set to true.
     */
    var isLicenseNotice: Boolean = false,
    /**
     * The [score] of the scanner for this license
     */
    var score: Double = 0.0,
) : TextEntry {
    companion object : Comparator<LicenseTextEntry> {

        override fun compare(a: LicenseTextEntry, b: LicenseTextEntry): Int  {
            if (a.license == b.license) {
                if ((a.isLicenseText && b.isLicenseText) || (!a.isLicenseText && !b.isLicenseText)) return 0
                if (a.isLicenseText && !b.isLicenseText) return -1
                if (!a.isLicenseText && b.isLicenseText) return 1
            }
            else {
                val aa = a.license?:""
                val bb = b.license?:""
                return aa.compareTo(bb)
            }
            return 0
        }
    }

}
