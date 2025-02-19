/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.api.MainClassFinder.Result;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link MainClassFinder}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MainClassFinderTest {

  @Mock private Consumer<LogEvent> logEventConsumer;

  @ParameterizedTest
  @CsvSource({
    "core/class-finder-tests/simple, HelloWorld",
    "core/class-finder-tests/subdirectories, multi.layered.HelloWorld",
    "core/class-finder-tests/extension, main.MainClass",
    "core/class-finder-tests/imported-methods, main.MainClass",
    "core/class-finder-tests/external-classes, main.MainClass",
    "core/class-finder-tests/inner-classes, HelloWorld$InnerClass",
    "core/class-finder-tests/varargs, HelloWorld",
    "core/class-finder-tests/synthetic, HelloWorldKt"
  })
  void testFindMainClass(String path, String t) throws URISyntaxException, IOException {
    Path rootDirectory = Paths.get(Resources.getResource(path).toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertSame(Result.Type.MAIN_CLASS_FOUND, mainClassFinderResult.getType());
    MatcherAssert.assertThat(
        mainClassFinderResult.getFoundMainClass(), CoreMatchers.containsString(t));
  }

  @Test
  void testFindMainClass_noClass() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("core/class-finder-tests/no-main").toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertEquals(Result.Type.MAIN_CLASS_NOT_FOUND, mainClassFinderResult.getType());
  }

  @Test
  void testFindMainClass_multiple() throws URISyntaxException, IOException {
    Path rootDirectory =
        Paths.get(Resources.getResource("core/class-finder-tests/multiple").toURI());
    MainClassFinder.Result mainClassFinderResult =
        MainClassFinder.find(new DirectoryWalker(rootDirectory).walk(), logEventConsumer);
    Assert.assertEquals(Result.Type.MULTIPLE_MAIN_CLASSES, mainClassFinderResult.getType());
    Assert.assertEquals(2, mainClassFinderResult.getFoundMainClasses().size());
    Assert.assertTrue(
        mainClassFinderResult.getFoundMainClasses().contains("multi.layered.HelloMoon"));
    Assert.assertTrue(mainClassFinderResult.getFoundMainClasses().contains("HelloWorld"));
  }
}
