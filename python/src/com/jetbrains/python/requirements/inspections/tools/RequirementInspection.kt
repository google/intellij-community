// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.tools

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.util.findParentOfType
import com.intellij.python.pyproject.PY_PROJECT_TOML_PROJECT
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.packaging.NonModulePackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.requirements.RequirementsFile
import com.jetbrains.python.requirements.getPythonSdk
import com.jetbrains.python.requirements.inspections.quickfixes.InstallAllRequirementsQuickFix
import com.jetbrains.python.requirements.inspections.quickfixes.InstallRequirementQuickFix
import com.jetbrains.python.requirements.inspections.quickfixes.PyGenerateRequirementsFileQuickFix
import com.jetbrains.python.requirements.inspections.quickfixes.UpdateAllRequirementQuickFix
import com.jetbrains.python.requirements.inspections.quickfixes.UpdateLockedDependenciesQuickFix
import com.jetbrains.python.requirements.inspections.quickfixes.UpdateRequirementQuickFix
import com.jetbrains.python.requirements.psi.Requirement
import com.jetbrains.python.sdk.isReadOnly
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

/**
 * For TOML injections, only `[project].dependencies` (PEP 621 main project dependencies) should
 * be checked for "must be installed" / "is outdated" problems. All other dependency-bearing
 * sections registered by [com.jetbrains.python.requirements.injection.TomlRequirementsInjectionSupport]
 * — `build-system`, `project.optional-dependencies`, `dependency-groups`,
 * `tool.uv.dev-dependencies`, and `tool.hatch.envs.*` — describe optional, conditional, or
 * build-time dependencies that may legitimately not be installed (and that the user has not
 * actively asked the IDE to keep in lock-step with the active interpreter).
 *
 * Classifying by TOML structure (rather than by what the package manager currently reports as
 * "main declared") avoids false negatives when the lockfile is stale: a freshly added entry in
 * `[project].dependencies` is recognized as main even before `uv lock`/`uv sync` runs.
 */
internal fun PsiElement.isInUninspectedTomlSection(): Boolean {
  val keyValue = findParentOfType<TomlKeyValue>() ?: return false
  val sectionName = keyValue.findParentOfType<TomlTable>()?.header?.key?.text ?: return false
  val fieldName = keyValue.key.text
  return !(sectionName == PY_PROJECT_TOML_PROJECT && fieldName == "dependencies")
}

/**
 * Combined inspection for two distinct issues that both target the same `[project].dependencies`
 * scope and share the same SDK / package-manager lookup, so they're folded into one inspection
 * rather than two:
 *
 *  - A requirement names a package that is **not installed** in the active interpreter →
 *    "Package <name> is not installed", with quick fixes [InstallRequirementQuickFix] and
 *    [InstallAllRequirementsQuickFix].
 *  - A requirement names a package that **is installed but outdated** (a newer version is
 *    available in the configured repository) → "Requirement <name>=<v>, latest is <v>", with
 *    quick fixes [UpdateRequirementQuickFix] and [UpdateAllRequirementQuickFix].
 *
 * Both use [isInUninspectedTomlSection] for scope filtering — previously the outdated check
 * fired in `[build-system]`, `[dependency-groups]`, etc. because that filter only existed in
 * the not-installed inspection; that was a bug, since there's no reason to nag about an
 * outdated build-system or dev dependency relative to the active project interpreter.
 */
class RequirementInspection : LocalInspectionTool() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = object : PsiElementVisitor() {
    override fun visitElement(element: PsiElement) {
      super.visitElement(element)

      val requirementsFile = element as? RequirementsFile ?: return
      val psiFile = session.file

      val isPyProjectInjection = psiFile.getUserData(FileContextUtil.INJECTED_IN_ELEMENT)?.element?.also {
        if (it.isInUninspectedTomlSection()) return@visitElement
      } != null

      val packageManager = getPythonSdk(session.file)
                             ?.takeIf { !it.isReadOnly }
                             ?.let { PythonPackageManager.forSdk(session.file.project, it) }
                             ?.takeIf { it.isInstalledPackagesLoaded }
                           ?: return

      if (!isPyProjectInjection) {
        verifyEmptyRequirementsTxt()
      }

      packageManager.verifyPackageManagerIssues(isPyProjectInjection, requirementsFile)
    }

    fun verifyEmptyRequirementsTxt() {
      if (!session.file.text.isNullOrBlank()) return

      val fix = ModuleUtilCore.findModuleForPsiElement(session.file)?.let {
        PyGenerateRequirementsFileQuickFix(it)
      } ?: return

      holder.registerProblem(
        session.file,
        PyPsiBundle.message("INSP.package.requirements.requirements.file.empty"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        fix
      )
    }

    fun PythonPackageManager.verifyPackageManagerIssues(isPyProjectInjection: Boolean, requirementsFile: RequirementsFile) {
      val installedPackages = listInstalledPackagesSnapshot()
      val outdatedPackages = listOutdatedPackagesSnapshot()

      val notInstalled = mutableListOf<Pair<Requirement, PyRequirement>>()
      val outdated = mutableListOf<Pair<Requirement, PythonOutdatedPackage>>()
      val reservedNames = NonModulePackageName.moduleNames(project)

      for (req in requirementsFile.requirements()) {
        val parsed = PyRequirementParser.fromLine(req.text) ?: continue
        if (parsed.name in reservedNames) continue

        val installedPkg = installedPackages.firstOrNull { it.name == parsed.name }
        if (installedPkg == null) {
          notInstalled += req to parsed
        }
        else {
          val info = outdatedPackages[parsed.name]
          if (info != null && info.latestVersion != installedPkg.version) {
            outdated += req to PythonOutdatedPackage(info.name, installedPkg.version, info.latestVersion)
          }
        }
      }

      val useUpdateLockedFix = isPyProjectInjection && updateLockedAction() != null
      notInstalledProblems(notInstalled, useUpdateLockedFix)
      outdatedProblems(outdated)
    }


    private fun notInstalledProblems(notInstalled: List<Pair<Requirement, PyRequirement>>, useUpdateLockedFix: Boolean) {
      if (notInstalled.isEmpty()) return

      val updateLockedDependenciesQuickFix = if (useUpdateLockedFix) UpdateLockedDependenciesQuickFix() else null

      val installAllQuickFix = if (!useUpdateLockedFix && notInstalled.size > 1) {
        InstallAllRequirementsQuickFix(notInstalled.map { it.second })
      }
      else null

      for ((psiRequirement, pyRequirement) in notInstalled) {
        val installSingleQuickFix = if (!useUpdateLockedFix) InstallRequirementQuickFix(pyRequirement) else null
        val fixes = listOfNotNull(installSingleQuickFix, installAllQuickFix, updateLockedDependenciesQuickFix).toTypedArray<LocalQuickFix>()
        if (fixes.isNotEmpty()) {
          holder.registerProblem(
            psiRequirement,
            PyBundle.message("INSP.requirements.package.not.installed", psiRequirement.requirement),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            *fixes
          )
        }
      }
    }

    private fun outdatedProblems(outdated: List<Pair<Requirement, PythonOutdatedPackage>>) {
      if (outdated.isEmpty()) return

      val updateAllQuickFix = if (outdated.size > 1) {
        UpdateAllRequirementQuickFix(outdated.map { it.second.name }.toSet())
      }
      else null

      for ((psiRequirement, pythonOutdatedPackage) in outdated) {
        val description = PyBundle.message(
          "python.sdk.inspection.message.version.outdated.latest",
          psiRequirement.displayName,
          pythonOutdatedPackage.version,
          pythonOutdatedPackage.latestVersion,
        )
        val fixes = listOfNotNull(
          UpdateRequirementQuickFix(pythonOutdatedPackage.name),
          updateAllQuickFix
        ).toTypedArray()

        holder.registerProblem(
          psiRequirement,
          description,
          ProblemHighlightType.WEAK_WARNING,
          *fixes,
        )
      }
    }
  }


  override fun isDumbAware(): Boolean = true
}
