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

package org.ossreviewtoolkit.oscake.metadata

import com.fasterxml.jackson.annotation.JsonProperty

import java.io.File

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.oscake.common.ActionPackage
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.*
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.config.OSCakeConfigParams
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.OSCakeLogger
import org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel.utils.ProcessingPhase

/**
 * A [DistributorPackage] contains a distributor-action for a specific package, identified by an [id]. The instances
 * are created during processing the corresponding yml-files.
 */
internal data class DistributorPackage(
    /**
    * The [id] contains a package identification as defined in the [Identifier] class. The version information may
    * be stored as a specific version number, an IVY-expression (describing a range of versions) or may be empty (then
    * it will be valid for all version numbers).
    */
    override val id: Identifier,
    /**
    * [distributionBlock] contains a [DistributorBlock]
    */
    @get:JsonProperty("distribution") val distributionBlock: DistributorBlock,
) : ActionPackage(id) {

    /**
     * Changes the distribution according to the [distributionBlock]
     */
   override fun process(pack: Pack, archiveDir: File, logger: OSCakeLogger, fileStore: File?) {

       if (pack.distribution == distributionBlock.from || OSCakeConfigParams.ignoreFromChecks) {
           pack.distribution = distributionBlock.to
       } else {
           logger.log(
               "The kind of distribution \"${distributionBlock.from}\" is different to the Distributor \"from\"" +
                       " definition found in file: \"$belongsToFile\"!",
               Level.WARN,
               pack.id,
               phase = ProcessingPhase.METADATAMANAGER
           )
       }
    }
}
