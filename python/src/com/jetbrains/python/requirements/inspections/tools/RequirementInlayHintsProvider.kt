// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.tools

import com.intellij.codeInsight.hints.declarative.HintFontSize
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.HintMarginPadding
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.requirements.RequirementsFile
import com.jetbrains.python.requirements.getPythonSdk
import com.jetbrains.python.requirements.psi.NameReq
import org.toml.lang.psi.TomlLiteral

/**
 * Renders a small grey "✓ <version>" inlay after each requirement whose package is installed in
 * the active interpreter, with a hover tooltip spelling out the version. Replaces a previous
 * background-color annotator: the hint conveys the same "this is installed" signal AND the
 * version, without tinting the surrounding text or competing with syntax highlighting.
 *
 * Built on the declarative inlay API on purpose: the legacy `InlayHintsProvider<T>` is marked as
 * "very likely to be deprecated" in its own KDoc and the platform pushes new providers toward the
 * declarative API. The trade-off is that `PresentationTreeBuilder` does not yet expose
 * `icon(...)`, so the "installed" marker is a Unicode glyph instead of a real `AllIcons.*`
 * rendering.
 *
 * Registered for two languages because the inlay-hints framework dispatches providers by the
 * *host* file's language:
 *  - `Requirements` covers standalone `requirements.txt` files (`NameReq` lives directly in PSI).
 *  - `TOML` covers `pyproject.toml` dependency tables, where Requirements is an injection inside
 *    a `TomlLiteral`. The collector enumerates injected files for each TOML literal and anchors
 *    the inlay at the literal's end offset so the hint sits just past the closing quote.
 */
class RequirementInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
    val sdk = getPythonSdk(file)
    val installedVersions: Map<PyPackageName, @NlsSafe String> = sdk
      ?.let { PythonPackageManager.forSdk(file.project, it).listInstalledPackagesSnapshot() }
      ?.associate { PyPackageName.from(it.name) to it.version }
      .orEmpty()
    return Collector(installedVersions, InjectedLanguageManager.getInstance(file.project))
  }

  private class Collector(
    private val installedVersions: Map<PyPackageName, @NlsSafe String>,
    private val injectedLanguageManager: InjectedLanguageManager,
  ) : SharedBypassCollector {
    private val hintFormat: HintFormat = HintFormat.default
      .withFontSize(HintFontSize.ABitSmallerThanInEditor)
      .withHorizontalMargin(HintMarginPadding.MarginAndSmallerPadding)

    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      if (installedVersions.isEmpty()) return
      when (element) {
        is NameReq -> emitHint(element, hostEndOffset = element.textRange.endOffset, sink)
        is TomlLiteral -> emitForInjectedRequirements(element, sink)
      }
    }

    private fun emitForInjectedRequirements(host: TomlLiteral, sink: InlayTreeSink) {
      // Anchor at the literal's end offset so hints sit past the closing quote.
      val hostEndOffset = host.textRange.endOffset
      injectedLanguageManager.enumerate(host) { injectedPsi, _ ->
        if (injectedPsi !is RequirementsFile) return@enumerate
        injectedPsi.requirements().filterIsInstance<NameReq>().forEach { nameReq ->
          emitHint(nameReq, hostEndOffset, sink)
        }
      }
    }

    private fun emitHint(nameReq: NameReq, hostEndOffset: Int, sink: InlayTreeSink) {
      val parsed = PyRequirementParser.fromLine(nameReq.text) ?: return
      // Match by normalized package name only — ignoring the version constraint — so the hint
      // still appears when the user typed an unsatisfiable or in-progress constraint (e.g.
      // `requests>=123`). The hint's purpose is to surface the *installed* version; whether the
      // declared constraint allows it is a separate concern handled by the version-spec checks.
      val installedVersion = installedVersions[PyPackageName.from(parsed.name)] ?: return

      val hintText = PyBundle.message("INLAY.requirements.installed.version", installedVersion)
      val tooltip = PyBundle.message("INLAY.requirements.installed.tooltip", installedVersion)
      sink.addPresentation(
        position = InlineInlayPosition(offset = hostEndOffset, relatedToPrevious = true),
        tooltip = tooltip,
        hintFormat = hintFormat,
      ) {
        text(hintText)
      }
    }
  }
}
