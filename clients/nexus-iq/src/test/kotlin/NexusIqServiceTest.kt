/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.nexusiq

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.net.URI

class NexusIqServiceTest : WordSpec({
    "SecurityIssue" should {
        "return the correct scoring system for sonatype references" {
            val issue = NexusIqService.SecurityIssue(
                "${NexusIqService.SONATYPE_PREFIX}foo",
                1.7f,
                URI("https://security.example.org/an-issue")
            )

            issue.scoringSystem() shouldBe NexusIqService.CVSS3_SCORE
        }

        "return the correct scoring system for other references" {
            val issue = NexusIqService.SecurityIssue(
                "CVE-0815",
                2.7f,
                URI("https://security.example.org/another-issue")
            )

            issue.scoringSystem() shouldBe NexusIqService.CVSS2_SCORE
        }
    }
})
