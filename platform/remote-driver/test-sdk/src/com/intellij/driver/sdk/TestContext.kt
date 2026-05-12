package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service

@Remote("com.jetbrains.performancePlugin.TestContext", plugin = "com.jetbrains.performancePlugin")
interface TestContext {
  fun setActiveTestName(testName: String?)
}

fun Driver.setActiveTestNameInTestIde(testName: String?) {
  service<TestContext>().setActiveTestName(testName)
}