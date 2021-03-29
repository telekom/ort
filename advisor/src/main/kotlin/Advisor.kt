/*
 * Copyright (C) 2020 Bosch.IO GmbH
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.advisor

import java.io.File
import java.time.Instant
import java.util.ServiceLoader

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorRecord
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.Environment
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.showStackTrace

/**
 * The class to retrieve security advisories.
 */
abstract class Advisor(val advisorName: String, protected val config: AdvisorConfiguration) {
    companion object {
        private val LOADER = ServiceLoader.load(AdvisorFactory::class.java)!!

        /**
         * The list of all available advisors in the classpath.
         */
        val ALL by lazy { LOADER.iterator().asSequence().toList() }
    }

    fun retrieveVulnerabilityInformation(
        ortResultFile: File,
        skipExcluded: Boolean = false
    ): OrtResult {
        require(ortResultFile.isFile) {
            "The provided ORT result file '${ortResultFile.canonicalPath}' does not exist."
        }

        val startTime = Instant.now()

        val ortResult = ortResultFile.readValue<OrtResult>()

        requireNotNull(ortResult.analyzer) {
            "The provided ORT result file '${ortResultFile.canonicalPath}' does not contain an analyzer result."
        }

        val packages = ortResult.getPackages(skipExcluded).map { it.pkg }
        val results = runBlocking { retrievePackageVulnerabilities(packages) }
            .mapKeysTo(sortedMapOf()) { (pkg, _) -> pkg.id }

        val advisorRecord = AdvisorRecord(results)

        val endTime = Instant.now()

        val advisorRun = AdvisorRun(startTime, endTime, Environment(), config, advisorRecord)
        return ortResult.copy(advisor = advisorRun)
    }

    protected abstract suspend fun retrievePackageVulnerabilities(
        packages: List<Package>
    ): Map<Package, List<AdvisorResult>>

    protected fun createFailedResults(
        startTime: Instant,
        packages: List<Package>,
        t: Throwable
    ): Map<Package, List<AdvisorResult>> {
        val endTime = Instant.now()

        t.showStackTrace()

        val failedResults = listOf(
            AdvisorResult(
                vulnerabilities = emptyList(),
                advisor = AdvisorDetails(advisorName),
                summary = AdvisorSummary(
                    startTime = startTime,
                    endTime = endTime,
                    issues = listOf(
                        createAndLogIssue(
                            source = advisorName,
                            message = "Failed to retrieve security vulnerabilities from $advisorName: " +
                                    t.collectMessagesAsString()
                        )
                    )
                )
            )
        )

        return packages.associateWith { failedResults }
    }
}
