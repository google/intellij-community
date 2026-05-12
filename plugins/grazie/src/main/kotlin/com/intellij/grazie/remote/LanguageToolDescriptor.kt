// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.nio.file.Path
import kotlin.io.path.Path

// These checksums may be obtained by running the [LanguageToolBundleInfoTest]
private const val EN_CHECKSUM = "c838a09c0db7ff769e73544bccf03877"
private const val AR_CHECKSUM = "a0223b05811c0789366e9b6fc6f03f0a"
private const val AST_CHECKSUM = "c186ab679460c4c3ceb679d5189e2049"
private const val BE_CHECKSUM = "d64eff15058f15932a8a46ecde0867c1"
private const val BR_CHECKSUM = "88280f306b5884dd77057573cea884d5"
private const val CA_CHECKSUM = "184fa815edfa7afce86fa07e6054ffcb"
private const val DA_CHECKSUM = "86529d41fcc958db4ead499c1d388dd2"
private const val DE_CHECKSUM = "60b81559211f1f710d556b192cc777c0"
private const val EL_CHECKSUM = "498ce64713f78c8cb252fff0f9a650c2"
private const val EO_CHECKSUM = "a6c423c261fedeeb70843553b84181db"
private const val ES_CHECKSUM = "c76822f968e7128470c3c603b2475cd8"
private const val FA_CHECKSUM = "4433e2e1492d16b57506576bdeaa31af"
private const val FR_CHECKSUM = "8b350d021010632c6f723adf328688b3"
private const val GA_CHECKSUM = "b0570390175bea3b472e0238e56b6ed3"
private const val GL_CHECKSUM = "3b70bdf0621fef1ec81eb6d3f565415e"
private const val IT_CHECKSUM = "24026c98e4f21283d627ab2e4adfcfd1"
private const val JA_CHECKSUM = "863a17947c53603edc9d97a69ad7c5a8"
private const val KM_CHECKSUM = "3dd0a545f38c258a832f0f7027325d9f"
private const val NL_CHECKSUM = "4b75f565c842adb118828db297b1bcf7"
private const val PL_CHECKSUM = "ca344c5e7a918d0c898e4d0bd0a3fb30"
private const val PT_CHECKSUM = "b29d6338849f2ac1b912eebadcbfa4c2"
private const val RO_CHECKSUM = "ed8a83e989fd0d14d8f7f65a3866e788"
private const val RU_CHECKSUM = "47a6f1f0858f5f1b436508cb228b6ce0"
private const val SK_CHECKSUM = "669568d729e8d78243d27adcbc57ee9a"
private const val SL_CHECKSUM = "0b4fc277cadc69a9851a059ad97e9ceb"
private const val SV_CHECKSUM = "135d9cd06ed78bf942e7a08a3e6f7812"
private const val TA_CHECKSUM = "34582bea348129ac8b3010c1d2c5cde2"
private const val TL_CHECKSUM = "1b825a2c055027a1bf3c6ee1de33b239"
private const val UK_CHECKSUM = "88af60248be71b4c42f97512a22ac33f"
private const val ZH_CHECKSUM = "f8f2e544f34684de209e9fb46890b78f"

enum class LanguageToolDescriptor(
  val langsClasses: List<String>,
  override val size: Int,
  override val iso: LanguageISO,
  override val checksum: String,
) : RemoteLangDescriptor {
  ARABIC(listOf("Arabic"), 13, LanguageISO.AR, AR_CHECKSUM),
  ASTURIAN(listOf("Asturian"), 1, LanguageISO.AST, AST_CHECKSUM),
  BELARUSIAN(listOf("Belarusian"), 1, LanguageISO.BE, BE_CHECKSUM),
  BRETON(listOf("Breton"), 2, LanguageISO.BR, BR_CHECKSUM),
  CATALAN(listOf("Catalan", "ValencianCatalan", "BalearicCatalan"), 4, LanguageISO.CA, CA_CHECKSUM),
  DANISH(listOf("Danish"), 1, LanguageISO.DA, DA_CHECKSUM),
  GERMAN(listOf("GermanyGerman", "AustrianGerman", "SwissGerman"), 20, LanguageISO.DE, DE_CHECKSUM),
  GREEK(listOf("Greek"), 1, LanguageISO.EL, EL_CHECKSUM),
  ENGLISH(
    listOf("BritishEnglish", "AmericanEnglish", "CanadianEnglish", "AustralianEnglish"),
    16,
    LanguageISO.EN,
    EN_CHECKSUM
  ),
  ESPERANTO(listOf("Esperanto"), 1, LanguageISO.EO, EO_CHECKSUM),
  SPANISH(listOf("Spanish"), 3, LanguageISO.ES, ES_CHECKSUM),
  PERSIAN(listOf("Persian"), 1, LanguageISO.FA, FA_CHECKSUM),
  FRENCH(listOf("French"), 2, LanguageISO.FR, FR_CHECKSUM),
  IRISH(listOf("Irish"), 13, LanguageISO.GA, GA_CHECKSUM),
  GALICIAN(listOf("Galician"), 5, LanguageISO.GL, GL_CHECKSUM),
  ITALIAN(listOf("Italian"), 1, LanguageISO.IT, IT_CHECKSUM),
  JAPANESE(listOf("Japanese"), 21, LanguageISO.JA, JA_CHECKSUM),
  KHMER(listOf("Khmer"), 1, LanguageISO.KM, KM_CHECKSUM),
  DUTCH(listOf("Dutch"), 37, LanguageISO.NL, NL_CHECKSUM),
  POLISH(listOf("Polish"), 5, LanguageISO.PL, PL_CHECKSUM),
  PORTUGUESE(
    listOf("PortugalPortuguese", "BrazilianPortuguese", "AngolaPortuguese", "MozambiquePortuguese"),
    5,
    LanguageISO.PT,
    PT_CHECKSUM
  ),
  ROMANIAN(listOf("Romanian"), 2, LanguageISO.RO, RO_CHECKSUM),
  RUSSIAN(listOf("Russian"), 5, LanguageISO.RU, RU_CHECKSUM),
  SLOVAK(listOf("Slovak"), 3, LanguageISO.SK, SK_CHECKSUM),
  SLOVENIAN(listOf("Slovenian"), 1, LanguageISO.SL, SL_CHECKSUM),
  SWEDISH(listOf("Swedish"), 1, LanguageISO.SV, SV_CHECKSUM),
  TAMIL(listOf("Tamil"), 1, LanguageISO.TA, TA_CHECKSUM),
  TAGALOG(listOf("Tagalog"), 1, LanguageISO.TL, TL_CHECKSUM),
  UKRAINIAN(listOf("Ukrainian"), 7, LanguageISO.UK, UK_CHECKSUM),
  CHINESE(listOf("Chinese"), 8, LanguageISO.ZH, ZH_CHECKSUM);

  override val storageName: String by lazy { "$iso-${GraziePlugin.LanguageTool.version}.jar" }
  override val file: Path by lazy { Path(storageName) }
  override val url: String by lazy { "${GraziePlugin.LanguageTool.url}/${GraziePlugin.LanguageTool.version}/$storageName" }
}
