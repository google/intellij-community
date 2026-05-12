package com.intellij.terminal.frontend.view.impl

import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyEvent

@ApiStatus.Internal
interface TerminalKeyEventsHandler {
  fun keyTyped(e: TimedKeyEvent) {}
  fun keyPressed(e: TimedKeyEvent) {}
}

internal fun TerminalKeyEventsHandler.handleKeyEvent(e: TimedKeyEvent) {
  if (e.original.id == KeyEvent.KEY_TYPED) {
    keyTyped(e)
  }
  else if (e.original.id == KeyEvent.KEY_PRESSED) {
    keyPressed(e)
  }
}
