// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.target.java.JavaTargetParameter;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@RunWith(JUnit4.class)
public class JaCoCoRunnerTest extends HeavyPlatformTestCase {
  @Test
  public void excludeIncludePatterns() {
    SimpleJavaParameters javaParameters = new SimpleJavaParameters();
    new JaCoCoCoverageRunner().appendCoverageArgument("a", null, new String[]{"org.*", "com.*"}, javaParameters, true, true, null, null);
    Assert.assertTrue(Pattern.compile("-javaagent:(.*)jacocoagent(.*).jar=destfile=a,append=false,inclnolocationclasses=true,excludes=org\\.\\*:com\\.\\*")
                        .matcher(String.join("", javaParameters.getTargetDependentParameters().toLocalParameters())).matches());
  }

  @Test
  public void includeAndExcludePatterns() {
    JavaTargetParameter parameter = new JaCoCoCoverageRunner().createArgumentTargetValue("jacocoagent.jar",
                                                                                        "coverage.exec",
                                                                                        new String[]{"foo.*", "bar.Baz"},
                                                                                        new String[]{"org.*"});
    Assert.assertEquals("-javaagent:jacocoagent.jar=destfile=coverage.exec,append=false,inclnolocationclasses=true,includes=foo.*:bar.Baz,excludes=org.*",
                        parameter.toLocalParameter());
  }

  @Test
  public void canBeLoadedAcceptsJaCoCoExecReport() {
    File report = new File(PluginPathManager.getPluginHomePath("coverage"), "testData/simple/simple$foo_in_simple.exec");

    Assert.assertTrue(new JaCoCoCoverageRunner().canBeLoaded(report.toPath()));
  }

  @Test
  public void canBeLoadedRejectsInvalidReport() throws IOException {
    File report = FileUtil.createTempFile("invalid-jacoco", ".exec", true);
    FileUtil.writeToFile(report, "not a jacoco report".getBytes(StandardCharsets.UTF_8));

    Assert.assertFalse(new JaCoCoCoverageRunner().canBeLoaded(report.toPath()));
  }
}
