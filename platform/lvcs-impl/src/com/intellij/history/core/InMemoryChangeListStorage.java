// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core;

import com.intellij.history.core.changes.ChangeSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

@ApiStatus.Internal
public final class InMemoryChangeListStorage implements ChangeListStorage {
  private int myCurrentId;
  private final List<ChangeSet> mySets = new ArrayList<>();

  @Override
  public void close(boolean drop) {
  }

  @Override
  public void flush() {
  }

  @Override
  public long nextId() {
    return myCurrentId++;
  }

  @Override
  public @NotNull Iterator<@NotNull ChangeSet> iterate() {
    return new Iterator<>() {
      private int index = mySets.size() - 1;

      @Override
      public boolean hasNext() {
        return index >= 0;
      }

      @Override
      public ChangeSet next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return mySets.get(index--);
      }
    };
  }

  @Override
  public void writeNextSet(@NotNull ChangeSet changeSet) {
    mySets.add(changeSet);
  }

  @Override
  public void purge(long period, long intervalBetweenActivities) {
  }
}
