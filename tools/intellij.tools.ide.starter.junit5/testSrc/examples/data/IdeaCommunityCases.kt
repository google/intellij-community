package examples.data

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.tools.ide.starter.product.idea.community.IdeaCommunity

object IdeaCommunityCases : TestCaseTemplate(IdeInfo.IdeaCommunity) {

  val GradleJitPackSimple = withProject(
    GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "/jitpack/gradle-simple",
      commitHash = "c11de3b42af65dd14c58d175c6ce0deb629704d6"
    )
  ).useRelease()
}