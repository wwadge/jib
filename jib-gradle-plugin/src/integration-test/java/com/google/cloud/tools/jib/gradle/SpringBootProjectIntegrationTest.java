/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.IntegrationTestingConfiguration;
import com.google.cloud.tools.jib.api.HttpRequestTester;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.security.DigestException;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Integration tests for building Spring Boot images. */
class SpringBootProjectIntegrationTest {

  @TempDir Path tempDir;

  @ClassRule public final TestProject springBootProject = new TestProject("spring-boot", tempDir);

  @Nullable private String containerName;

  @After
  void tearDown() throws IOException, InterruptedException {
    if (containerName != null) {
      new Command("docker", "stop", containerName).run();
    }
  }

  @Test
  void testBuild_packagedMode() throws IOException, InterruptedException, DigestException {
    buildAndRunWebApp("springboot:gradle", "build.gradle");

    String output =
        new Command(
                "docker",
                "exec",
                containerName,
                "/busybox/wc",
                "-c",
                "/app/classpath/spring-boot-original.jar")
            .run();

    Assert.assertEquals("1360 /app/classpath/spring-boot-original.jar\n", output);
    HttpRequestTester.verifyBody(
        "Hello world",
        new URL("http://" + HttpRequestTester.fetchDockerHostForHttpRequest() + ":8080"));
  }

  private void buildAndRunWebApp(String label, String gradleBuildFile)
      throws IOException, InterruptedException, DigestException {
    String nameBase = IntegrationTestingConfiguration.getTestRepositoryLocation() + '/';
    String targetImage = nameBase + label + System.nanoTime();
    String output =
        JibRunHelper.buildAndRun(
            springBootProject, targetImage, gradleBuildFile, "--detach", "-p8080:8080");
    containerName = output.trim();
  }
}
