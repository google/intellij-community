// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import java.nio.ByteBuffer

internal fun ByteBuffer.putPartially(src: ByteBuffer): Int {
  val dst = this
  val bytesBeforeRead = src.remaining()
  // Choose the best approach:
  if (src.remaining() <= dst.remaining()) {
    // Bulk put the whole buffer
    dst.put(src)
  }
  else {
    // Slice, put, and set size back
    val l = src.limit()
    dst.put(src.limit(src.position() + dst.remaining()))
    src.limit(l)
  }
  val bytesRead = bytesBeforeRead - src.remaining()
  return bytesRead
}
