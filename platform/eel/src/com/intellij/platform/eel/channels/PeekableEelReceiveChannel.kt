// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.channels

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.ReadResult.EOF
import com.intellij.platform.eel.ReadResult.NOT_EOF
import com.intellij.platform.eel.ThrowsChecked
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

@ApiStatus.Experimental
class PeekableEelReceiveChannel(private val delegate: EelReceiveChannel) : EelReceiveChannel {
  private val dataQueue = ArrayDeque<ByteBuffer>()

  fun prepend(vararg data: ByteBuffer) {
    data.reverse()
    for (d in data) {
      if (d.hasRemaining()) {
        dataQueue.addFirst(d)
      }
    }
  }

  @ThrowsChecked(EelReceiveChannelException::class)
  override suspend fun receive(dst: ByteBuffer): ReadResult {
    if (dataQueue.isNotEmpty()) {
      val oldDstPosition = dst.position()
      do {
        val head = dataQueue.removeFirstOrNull() ?: break
        val oldHeadLimit = head.limit()
        head.limit(oldHeadLimit.coerceAtMost(dst.remaining()))
        dst.put(head)
        head.limit(oldHeadLimit)
        if (head.hasRemaining()) {
          dataQueue.addFirst(head)
        }
      }
      while (dst.hasRemaining())

      if (dst.position() != oldDstPosition) {
        return ReadResult.NOT_EOF
      }
    }

    return delegate.receive(dst)
  }

  @ThrowsChecked(EelReceiveChannelException::class)
  @EelDelicateApi
  override fun available(): Int {
    return dataQueue.sumOf { it.remaining() } + delegate.available()
  }

  override suspend fun closeForReceive() {
    dataQueue.clear()
    delegate.closeForReceive()
  }

  override val prefersDirectBuffers: Boolean
    get() = delegate.prefersDirectBuffers
}

@ApiStatus.Experimental
fun EelReceiveChannel.peekable(): PeekableEelReceiveChannel =
  this as? PeekableEelReceiveChannel ?: PeekableEelReceiveChannel(this)

@ThrowsChecked(EelReceiveChannelException::class)
@ApiStatus.Experimental
suspend fun PeekableEelReceiveChannel.readLine(charset: Charset): String? {
  val result = ByteArrayOutputStream()
  val buffer = ByteBuffer.allocate(4096)
  var empty = true
  while (true) {
    var receivedEol = false
    when (receive(buffer)) {
      EOF -> break
      NOT_EOF -> {
        empty = false
      }
    }
    buffer.flip()
    while (buffer.hasRemaining()) {
      when (val b = buffer.get().toInt()) {
        '\n'.code -> {
          receivedEol = true
          break
        }
        '\r'.code -> {
          if (buffer.hasRemaining() && buffer.get().toInt() == '\n'.code) {
            receivedEol = true
            break
          }
          else {
            result.write(b)
          }
        }
        else -> result.write(b)
      }
    }
    if (receivedEol) {
      prepend(buffer)
      break
    }
    buffer.clear()
  }
  if (empty) {
    return null
  }
  return String(result.toByteArray(), charset)
}
