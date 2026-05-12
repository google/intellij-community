package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.community.JetBrainsDataServiceClient
import com.intellij.ide.starter.models.IdeInfoType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import kotlin.random.Random

class JetBrainsReleaseFilteringTest {
  @Test
  fun `getting N latest public releases`() {
    val numberOfReleases = Random.nextInt(2, 10)
    val latestReleases = JetBrainsDataServiceClient.getLatestPublicReleases(productType = IdeInfoType.IDEA_ULTIMATE.productCode,
                                                                            numberOfReleases = numberOfReleases)
    latestReleases.shouldHaveSize(numberOfReleases)
    latestReleases.forEach { it.date.toString().shouldNotBeEmpty() }
    latestReleases.map { it.build }.toSet().shouldHaveSize(numberOfReleases)
  }

  @Test
  fun `filtering only major version of public releases`() {
    val numberOfReleases = Random.nextInt(2, 10)
    val latestReleases = JetBrainsDataServiceClient.getLatestPublicReleaseVersions(productType = IdeInfoType.IDEA_ULTIMATE.productCode,
                                                                                   numberOfReleases = numberOfReleases)
    latestReleases.shouldHaveSize(numberOfReleases)
    latestReleases.toSet().shouldHaveSize(numberOfReleases)
  }
}