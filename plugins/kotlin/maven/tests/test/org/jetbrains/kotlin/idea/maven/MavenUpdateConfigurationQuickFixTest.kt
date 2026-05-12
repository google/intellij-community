// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven

import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenUpdateConfigurationQuickFixTest12 : AbstractMavenUpdateConfigurationQuickFixTest() {

    override val testRoot: String
        get() = "maven/tests/testData/languageFeature"

    @Test
    fun testUpdateLanguageVersion() = runBlocking {
        doTest("Increase language version to 2.2")
    }

    @Test
    fun testUpdateLanguageVersionProperty() = runBlocking {
        doTest("Increase language version to 2.2")
    }

    @Test
    fun testUpdateLanguageAndApiVersion() = runBlocking {
        doTest("Increase language version to 2.2")
    }

    @Test
    fun testAddKotlinReflect() = runBlocking {
        doTest("Add 'kotlin-reflect.jar' to the classpath")
    }
}