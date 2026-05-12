// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class MavenSourceRootsImportingTest : MavenMultiVersionImportingTestCase() {

  @Test
  fun testKotlinSourceRootsAreImportedWithSmartDefaults() = runBlocking {
    createProjectSubDirs("src/main/kotlin", "src/test/kotlin")

    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-plugin</artifactId>
            <version>2.3.20</version>
            <extensions>true</extensions>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())

    assertModules("project")
    assertSources("project", "src/main/java", "src/main/kotlin")
    assertTestSources("project", "src/test/java", "src/test/kotlin")
  }
}
