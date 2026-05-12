package com.intellij.ide.starter.utils

object SevenZipDistribution {
  private const val VERSION = "2501"

  const val WINDOWS_X64_URL: String = "https://www.7-zip.org/a/7z${VERSION}-x64.exe"
  const val WINDOWS_ARM64_URL: String = "https://www.7-zip.org/a/7z${VERSION}-arm64.exe"
  const val LINUX_X64_URL: String = "https://www.7-zip.org/a/7z${VERSION}-linux-x64.tar.xz"
}
