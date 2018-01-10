/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.downloader.vcs

import com.here.ort.model.VcsInfo
import com.here.ort.utils.Expensive

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = "https://bitbucket.org/creaceed/mercurial-xcode-plugin"
private const val REPO_REV = "02098fc8bdaca4739ec52cbcb8ed51654eacee25"
private const val REPO_PATH = "Classes"
private const val REPO_VERSION = "1.1"
private const val REPO_REV_FOR_VERSION = "562fed42b4f3dceaacf6f1051963c865c0241e28"
private const val REPO_PATH_FOR_VERSION = "Resources"

class MercurialTest : StringSpec() {
    private lateinit var outputDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        outputDir = createTempDir()
        try {
            super.interceptTestCase(context, test)
        } finally {
            outputDir.deleteRecursively()
        }
    }

    init {
        "Mercurial can download a given revision" {
            val vcs = VcsInfo("Mercurial", REPO_URL, REPO_REV, "")
            val expectedFiles = listOf(
                    ".hg",
                    ".hgsub",
                    ".hgsubstate",
                    "Classes",
                    "LICENCE.md",
                    "MercurialPlugin.xcodeproj",
                    "README.md",
                    "Resources",
                    "Script"
            )

            val workingTree = Mercurial.download(vcs, "", outputDir)
            val actualFiles = workingTree.workingDir.list()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }.config(tags = setOf(Expensive))

        "Mercurial can download only a single path" {
            val vcs = VcsInfo("Mercurial", REPO_URL, REPO_REV, REPO_PATH)
            val expectedFiles = listOf(
                    File(".hgsub"), // We always get these configuration files, if present.
                    File(".hgsubstate"),
                    File(REPO_PATH, "MercurialPlugin.h"),
                    File(REPO_PATH, "MercurialPlugin.m"),
                    File("Script", "git.py"), // As a submodule, "Script" is always included.
                    File("Script", "gpl-2.0.txt"),
                    File("Script", "install_bridge.sh"),
                    File("Script", "README"),
                    File("Script", "sniff.py"),
                    File("Script", "uninstall_bridge.sh")
            )

            val workingTree = Mercurial.download(vcs, "", outputDir)
            val actualFiles = workingTree.workingDir.walkBottomUp()
                    .onEnter { it.name != ".hg" }
                    .filter { it.isFile }
                    .map { it.relativeTo(outputDir) }

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }.config(tags = setOf(Expensive))

        "Mercurial can download based on a version" {
            val vcs = VcsInfo("Mercurial", REPO_URL, "", "")

            val workingTree = Mercurial.download(vcs, REPO_VERSION, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
        }.config(tags = setOf(Expensive))

        "Mercurial can download only a single path based on a version" {
            val vcs = VcsInfo("Mercurial", REPO_URL, "", REPO_PATH_FOR_VERSION)
            val expectedFiles = listOf(
                    File(".hgsub"), // We always get these configuration files, if present.
                    File(".hgsubstate"),
                    File(REPO_PATH_FOR_VERSION, "icon.icns"),
                    File(REPO_PATH_FOR_VERSION, "icon_blank.icns"),
                    File(REPO_PATH_FOR_VERSION, "Info.plist"),
                    File("Script", "git.py"), // As a submodule, "Script" is always included.
                    File("Script", "gpl-2.0.txt"),
                    File("Script", "install_bridge.sh"),
                    File("Script", "README"),
                    File("Script", "sniff.py"),
                    File("Script", "uninstall_bridge.sh")
            )

            val workingTree = Mercurial.download(vcs, REPO_VERSION, outputDir)
            val actualFiles = workingTree.workingDir.walkBottomUp()
                    .onEnter { it.name != ".hg" }
                    .filter { it.isFile }
                    .map { it.relativeTo(outputDir) }

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }.config(tags = setOf(Expensive))
    }
}
