/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.cli;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.api.HttpRequestTester;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class WarCommandTest {

  @ClassRule public static final TestProject servletProject = new TestProject("warTest");

  @Nullable private String containerName;

  @AfterEach
  void tearDown() throws IOException, InterruptedException {
    if (containerName != null) {
      new Command("docker", "stop", containerName).run();
    }
  }

  @Test
  void testErrorLogging_fileDoesNotExist() {
    StringWriter stringWriter = new StringWriter();
    CommandLine jibCli = new CommandLine(new JibCli()).setErr(new PrintWriter(stringWriter));

    Integer exitCode = jibCli.execute("war", "--target", "docker://jib-cli-image", "unknown.war");

    assertThat(exitCode).isEqualTo(1);
    assertThat(stringWriter.toString())
        .isEqualTo("[ERROR] The file path provided does not exist: unknown.war\n");
  }

  @Test
  void testErrorLogging_directoryGiven() {
    StringWriter stringWriter = new StringWriter();
    CommandLine jibCli = new CommandLine(new JibCli()).setErr(new PrintWriter(stringWriter));

    Path warFile = Paths.get("/");
    Integer exitCode =
        jibCli.execute("war", "--target", "docker://jib-cli-image", warFile.toString());

    assertThat(exitCode).isEqualTo(1);
    assertThat(stringWriter.toString())
        .isEqualTo(
            "[ERROR] The file path provided is for a directory. Please provide a path to a WAR: "
                + warFile.toString()
                + "\n");
  }

  @Test
  void testWar_jetty() throws IOException, InterruptedException {
    servletProject.build("clean", "war");
    Path warParentPath = servletProject.getProjectRoot().resolve("build").resolve("libs");
    Path warPath = warParentPath.resolve("standard-war.war");
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute("war", "--target", "docker://exploded-war", warPath.toString());
    assertThat(exitCode).isEqualTo(0);
    String output =
        new Command(
                "docker",
                "run",
                "--rm",
                "--detach",
                "-p8080:8080",
                "exploded-war",
                "--privileged",
                "--network=host")
            .run();
    containerName = output.trim();

    HttpRequestTester.verifyBody(
        "Hello world",
        new URL("http://" + HttpRequestTester.fetchDockerHostForHttpRequest() + ":8080/hello"));
  }

  @Test
  void testWar_customJettySpecified() throws IOException, InterruptedException {
    servletProject.build("clean", "war");
    Path warParentPath = servletProject.getProjectRoot().resolve("build").resolve("libs");
    Path warPath = warParentPath.resolve("standard-war.war");
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "war",
                "--target",
                "docker://exploded-war-custom-jetty",
                "--from=jetty:11.0-jre11-slim-openjdk",
                warPath.toString());
    assertThat(exitCode).isEqualTo(0);
    String output =
        new Command("docker", "run", "--rm", "--detach", "-p8080:8080", "exploded-war-custom-jetty")
            .run();
    containerName = output.trim();

    HttpRequestTester.verifyBody(
        "Hello world",
        new URL("http://" + HttpRequestTester.fetchDockerHostForHttpRequest() + ":8080/hello"));
  }

  @Test
  void testWar_tomcat() throws IOException, InterruptedException {
    servletProject.build("clean", "war");
    Path warParentPath = servletProject.getProjectRoot().resolve("build").resolve("libs");
    Path warPath = warParentPath.resolve("standard-war.war");
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute(
                "war",
                "--target",
                "docker://exploded-war-tomcat",
                "--from=tomcat:10-jre8-openjdk-slim",
                "--app-root",
                "/usr/local/tomcat/webapps/ROOT",
                warPath.toString());
    assertThat(exitCode).isEqualTo(0);
    String output =
        new Command("docker", "run", "--rm", "--detach", "-p8080:8080", "exploded-war-tomcat")
            .run();
    containerName = output.trim();

    HttpRequestTester.verifyBody(
        "Hello world",
        new URL("http://" + HttpRequestTester.fetchDockerHostForHttpRequest() + ":8080/hello"));
  }
}
