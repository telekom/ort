/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.test.containExactly

class ScopeTest : WordSpec({
    "getDependencyTreeDepth()" should {
        "return 0 if it does not contain any package" {
            val scope = Scope(name = "test", dependencies = sortedSetOf())

            scope.getDependencyTreeDepth() shouldBe 0
        }

        "return 1 if it contains only direct dependencies" {
            val scope = Scope(
                name = "test",
                dependencies = sortedSetOf(
                    PackageReference(id = Identifier("a")),
                    PackageReference(id = Identifier("b"))
                )
            )

            scope.getDependencyTreeDepth() shouldBe 1
        }

        "return 2 if it contains a tree of height 2" {
            val scope = Scope(
                name = "test",
                dependencies = sortedSetOf(
                    pkg("a") {
                        pkg("a1")
                    }
                )
            )

            scope.getDependencyTreeDepth() shouldBe 2
        }

        "return 3 if it contains a tree of height 3" {
            val scope = Scope(
                name = "test",
                dependencies = sortedSetOf(
                    pkg("a") {
                        pkg("a1") {
                            pkg("a11")
                            pkg("a12")
                        }
                    },
                    pkg("b")
                )
            )

            scope.getDependencyTreeDepth() shouldBe 3
        }
    }

    "getShortestPaths()" should {
        "find the shortest path to each dependency" {
            val scope = Scope(
                name = "test",
                dependencies = sortedSetOf(
                    pkg("A"),
                    pkg("B") {
                        pkg("A")
                    },
                    pkg("C") {
                        pkg("B") {
                            pkg("A") {
                                pkg("H")
                                pkg("I") {
                                    pkg("H")
                                }
                            }
                        }
                        pkg("D") {
                            pkg("E")
                        }
                    },
                    pkg("F") {
                        pkg("E") {
                            pkg("I")
                        }
                    },
                    pkg("G") {
                        pkg("E")
                    }
                )
            )

            scope.getShortestPaths() should containExactly(
                Identifier("A") to emptyList(),
                Identifier("B") to emptyList(),
                Identifier("C") to emptyList(),
                Identifier("D") to listOf(Identifier("C")),
                Identifier("E") to listOf(Identifier("F")),
                Identifier("F") to emptyList(),
                Identifier("G") to emptyList(),
                Identifier("H") to listOf(Identifier("C"), Identifier("B"), Identifier("A")),
                Identifier("I") to listOf(Identifier("F"), Identifier("E"))
            )
        }
    }
})

private class PackageReferenceBuilder(id: String) {
    private val id = Identifier(id)
    private val dependencies = sortedSetOf<PackageReference>()

    fun pkg(id: String, block: PackageReferenceBuilder.() -> Unit = {}) {
        dependencies += PackageReferenceBuilder(id).apply { block() }.build()
    }

    fun build(): PackageReference = PackageReference(id = id, dependencies = dependencies)
}

private fun pkg(id: String, block: PackageReferenceBuilder.() -> Unit = {}): PackageReference =
    PackageReferenceBuilder(id).apply { block() }.build()
