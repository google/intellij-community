// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.impl.enrichLocalPythonSdkWithHomeInfo
import com.jetbrains.python.sdk.impl.pythonEnvironmentCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * An [Sdk] paired with the outcome of [PythonEnvironment] detection.
 *
 * [PyRichSdk] has a snaphost of cached environment info - [environmentResult]:
 *  - `null` — nothing has been detected (non-Python / remote SDK);
 *  - [PyResult] failure — detection ran but failed (bad home path, unreadable layout, …);
 *  - [PyResult] success — the detected [PythonEnvironment].
 *
 * Convenience accessors ([pythonEnvironment], [pythonHomePath], [pythonBinaryPath]) reach into the successful result
 * and return `null` for all other cases, so callers that only want the common path can use them
 * without unwrapping.
 *
 * Obtain one via:
 *  - [Sdk.pyRichSdk] — background-thread factory that triggers detection when the cache is cold;
 *    always returns a non-null wrapper.
 *  - [Sdk.pyRichSdkOrNull] — synchronous read; returns `null` only when nothing has ever been
 *    cached for the SDK.
 *
 * [PyRichSdk] implements [Sdk] by delegation, so it can be passed anywhere an [Sdk] is expected.
 */
@ApiStatus.Internal
class PyRichSdk internal constructor(
  val sdk: Sdk,
  val environmentResult: PyResult<PythonEnvironment>?,
) : Sdk by sdk {
  /** The detected environment on success; `null` when [environmentResult] is `null` or a failure. */
  val pythonEnvironment: PythonEnvironment?
    get() = environmentResult?.successOrNull

  /** Absolute path to the Python interpreter executable from the detected environment; `null` when detection did not succeed. */
  val pythonBinaryPath: PythonBinary?
    get() = pythonEnvironment?.pythonBinaryPath

  /** Environment root (venv / conda prefix) when the detected environment has one; `null` otherwise. */
  val pythonHomePath: PythonHomePath?
    get() = (pythonEnvironment as? HasPythonHome)?.pythonHomePath

  val isActivatable: Boolean
    get() = pythonEnvironment is Activatable
}

/**
 * A [PyRichSdk] wrapping whatever [PythonEnvironment] detection has already cached for this SDK,
 * or `null` when nothing has ever been cached (early startup, non-Python / remote SDK).
 *
 * Synchronous, no I/O. A non-null wrapper whose [PyRichSdk.environmentResult] is a failure still
 * counts as "already attempted". For a guaranteed fresh result, call [pyRichSdk] on a background
 * thread.
 */
@get:ApiStatus.Internal
val Sdk.pyRichSdkOrNull: PyRichSdk?
  get() = pythonEnvironmentCache?.let { PyRichSdk(this, it) }

/**
 * Ensures this SDK is enriched with [PythonEnvironment] information and returns a [PyRichSdk].
 *
 * Always returns a non-null wrapper — inspect [PyRichSdk.environmentResult] to distinguish:
 *  - `null` — the SDK is non-Python or remote and was not enriched;
 *  - failure — detection ran but failed (e.g. bad home path);
 *  - success — the [PythonEnvironment] was detected.
 *
 * After a successful call on a local Python SDK, subsequent [pyRichSdkOrNull] reads on this SDK
 * are non-null until the cache is refreshed.
 *
 * Triggers file I/O on the calling thread via the underlying detector; must not be called on EDT.
 *
 * @param forceRefresh re-detect even if a cached result already exists.
 */
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun Sdk.pyRichSdk(forceRefresh: Boolean = false): PyRichSdk {
  val baseSdk = if (this is PyRichSdk) sdk else this
  return PyRichSdk(baseSdk, enrichLocalPythonSdkWithHomeInfo(forceRefresh))
}

@ApiStatus.Internal
suspend fun Sdk.pyRichSdkAsync(forceRefresh: Boolean = false): PyRichSdk = withContext(Dispatchers.IO) {
  this@pyRichSdkAsync.pyRichSdk(forceRefresh)
}
