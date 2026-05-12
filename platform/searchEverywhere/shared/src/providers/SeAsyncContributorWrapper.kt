// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereExtendedInfoProvider
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicatorBase
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.StandardProgressIndicator
import com.intellij.openapi.progress.WrappedProgressIndicator
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfoBuilder
import com.intellij.platform.searchEverywhere.providers.SeLog.ITEM_EMIT
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeAsyncContributorWrapper<I : Any>(val contributor: SearchEverywhereContributor<I>) : Disposable {
  suspend fun fetchElements(pattern: String, consumer: AsyncProcessor<I>, operationDisposable: Disposable? = null) {
    if (pattern.isEmpty() && !contributor.isEmptyPatternSupported) return

    coroutineToIndicator { indicator ->
      val attemptIndicator = RetryProgressIndicator(indicator)
      try {
        fetchElementsOnce(pattern, attemptIndicator, consumer, operationDisposable)
      }
      catch (e: ProcessCanceledException) {
        if (!attemptIndicator.isCanceled) {
          throw e
        }
      }
      catch (e: CancellationException) {
        if (!attemptIndicator.isCanceled) {
          throw e
        }
      }

      if (attemptIndicator.isCanceled) {
        throw ProcessCanceledException()
      }
    }
  }

  private fun fetchElementsOnce(
    pattern: String,
    indicator: ProgressIndicator,
    consumer: AsyncProcessor<I>,
    operationDisposable: Disposable?,
  ) {
    if (contributor is WeightedSearchEverywhereContributor) {
      if (operationDisposable == null) {
        contributor.fetchWeightedElements(pattern, indicator) { t ->
          runBlockingCancellable {
            SeLog.log(ITEM_EMIT) {
              "Provider async wrapper of ${contributor.searchProviderId} emitting: ${t.item.toString().split('\n').firstOrNull()}"
            }
            consumer.process(t.item, t.weight)
          }
        }
      }
      else {
        contributor.fetchWeightedElementsWithOperationDisposable(pattern, indicator, operationDisposable) { t ->
          runBlockingCancellable {
            SeLog.log(ITEM_EMIT) {
              "Provider async wrapper of ${contributor.searchProviderId} emitting: ${t.item.toString().split('\n').firstOrNull()}"
            }
            consumer.process(t.item, t.weight)
          }
        }
      }
    }
    else {
      contributor.fetchElements(pattern, indicator) { t ->
        runBlockingCancellable {
          SeLog.log(ITEM_EMIT) {
            "Provider async wrapper of ${contributor.searchProviderId} emitting: ${t.toString().split('\n').firstOrNull()}"
          }
          val weight = contributor.getElementPriority(t, pattern)
          consumer.process(t, weight)
        }
      }
    }
  }

  override fun dispose() {
    Disposer.dispose(contributor)
  }
}

private class RetryProgressIndicator(
  private val delegate: ProgressIndicator,
) : EmptyProgressIndicatorBase(delegate.modalityState), WrappedProgressIndicator, StandardProgressIndicator {
  @Volatile
  private var canceled = false

  override fun cancel() {
    canceled = true
  }

  override fun isCanceled(): Boolean = canceled || delegate.isCanceled

  override fun setText(text: String?) {
    delegate.text = text
  }

  override fun getText(): String? = delegate.text

  override fun setText2(text: String?) {
    delegate.text2 = text
  }

  override fun getText2(): String? = delegate.text2

  override fun getFraction(): Double = delegate.fraction

  override fun setFraction(fraction: Double) {
    delegate.fraction = fraction
  }

  override fun pushState() {
    delegate.pushState()
  }

  override fun popState() {
    delegate.popState()
  }

  override fun startNonCancelableSection() {
    super<EmptyProgressIndicatorBase>.startNonCancelableSection()
    delegate.startNonCancelableSection()
  }

  override fun finishNonCancelableSection() {
    super<EmptyProgressIndicatorBase>.finishNonCancelableSection()
    delegate.finishNonCancelableSection()
  }

  override fun isModal(): Boolean = delegate.isModal

  override fun setModalityProgress(modalityProgress: ProgressIndicator?) {
    delegate.setModalityProgress(modalityProgress)
  }

  override fun isIndeterminate(): Boolean = delegate.isIndeterminate

  override fun setIndeterminate(indeterminate: Boolean) {
    delegate.isIndeterminate = indeterminate
  }

  override fun isPopupWasShown(): Boolean = delegate.isPopupWasShown

  override fun isShowing(): Boolean = delegate.isShowing

  override fun getOriginalProgressIndicator(): ProgressIndicator = delegate
}

@Internal
interface AsyncProcessor<T> {
  suspend fun process(item: T, weight: Int): Boolean
}

@Internal
fun SearchEverywhereContributor<*>.getExtendedInfo(item: Any): SeExtendedInfo {
  val extendedInfo = (this as? SearchEverywhereExtendedInfoProvider)?.createExtendedInfo()
  return SeExtendedInfoBuilder().withExtendedInfo(extendedInfo, item).build()
}
