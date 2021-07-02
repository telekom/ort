/*
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.scanner.experimental

import java.io.IOException

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.utils.createOrtTempDir

/**
 * The [ProvenanceTreeResolver] provides a function to resolve nested provenances.
 */
interface ProvenanceTreeResolver {
    /**
     * Resolve nested [Provenance]s of the provided [provenance]. For an [ArtifactProvenance] the returned
     * [ProvenanceTree] always contains only the provided [ArtifactProvenance]. For a [RepositoryProvenance] the
     * resolver looks for nested repositories, for example Git submodules or Mercurial subrepositories.
     */
    fun resolveProvenanceTree(provenance: KnownProvenance): ProvenanceTree
}

/**
 * The default implementation of [ProvenanceTreeResolver].
 */
class DefaultProvenanceTreeResolver : ProvenanceTreeResolver {
    override fun resolveProvenanceTree(provenance: KnownProvenance): ProvenanceTree {
        return when (provenance) {
            is ArtifactProvenance -> ProvenanceTree(root = provenance, subRepositories = emptyMap())
            is RepositoryProvenance -> resolveVcsSourceTree(provenance)
        }
    }

    private fun resolveVcsSourceTree(provenance: RepositoryProvenance): ProvenanceTree {
        val vcs = VersionControlSystem.forType(provenance.vcsInfo.type)
            ?: VersionControlSystem.forUrl(provenance.vcsInfo.url)
            ?: throw IOException("Could not determine VCS type for ${provenance.vcsInfo}.")

        val targetDir = createOrtTempDir()
        val vcsInfo = provenance.vcsInfo.copy(revision = provenance.resolvedRevision)

        val workingTree = vcs.initWorkingTree(targetDir, vcsInfo)

        val result = vcs.updateWorkingTree(workingTree, vcsInfo.revision, path = vcsInfo.path, recursive = true)
        result.onFailure { throw it }

        val subRepositories = workingTree.getNested().mapValues { (_, nestedVcs) ->
            // TODO: Verify that the revision is always a resolved revision.
            RepositoryProvenance(nestedVcs, nestedVcs.revision)
        }

        return ProvenanceTree(root = provenance, subRepositories = subRepositories)
    }
}
