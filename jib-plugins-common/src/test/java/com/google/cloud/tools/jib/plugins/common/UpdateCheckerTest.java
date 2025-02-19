/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.http.TestWebServer;
import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link UpdateChecker}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateCheckerTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();
  @TempDir public Path temporaryFolder;

  private TestWebServer testWebServer;
  private Path configDir;

  @BeforeEach
  void setUpBeforeEach()
      throws InterruptedException, GeneralSecurityException, URISyntaxException, IOException {
    String response = "HTTP/1.1 200 OK\nContent-Length:18\n\n{\"latest\":\"2.0.0\"}";
    testWebServer = new TestWebServer(false, Collections.singletonList(response), 1);
    configDir = temporaryFolder;
  }

  @AfterEach
  void tearDownAfterEach() throws IOException {
    testWebServer.close();
  }

  @Test
  void testPerformUpdateCheck_newVersionFound() throws IOException, InterruptedException {
    Instant before = Instant.now();
    Thread.sleep(100);
    setupLastUpdateCheck();
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            configDir, "1.0.2", testWebServer.getEndpoint(), "tool-name", ignored -> {});

    assertThat(testWebServer.getInputRead()).contains("User-Agent: jib 1.0.2 tool-name");
    assertThat(message).hasValue("2.0.0");
    String modifiedTime =
        new String(
            Files.readAllBytes(configDir.resolve("lastUpdateCheck")), StandardCharsets.UTF_8);
    assertThat(Instant.parse(modifiedTime)).isGreaterThan(before);
  }

  @Test
  void testPerformUpdateCheck_newJsonField()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    String response =
        "HTTP/1.1 200 OK\nContent-Length:43\n\n{\"latest\":\"2.0.0\",\"unknownField\":\"unknown\"}";
    try (TestWebServer server = new TestWebServer(false, Collections.singletonList(response), 1)) {
      setupLastUpdateCheck();
      Optional<String> message =
          UpdateChecker.performUpdateCheck(
              configDir, "1.0.2", server.getEndpoint(), "tool-name", ignored -> {});

      assertThat(message).hasValue("2.0.0");
    }
  }

  @Test
  void testPerformUpdateCheck_onLatest() throws IOException, InterruptedException {
    Instant before = Instant.now();
    Thread.sleep(100);
    setupLastUpdateCheck();
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            configDir, "2.0.0", testWebServer.getEndpoint(), "tool-name", ignored -> {});

    assertThat(message).isEmpty();
    String modifiedTime =
        new String(
            Files.readAllBytes(configDir.resolve("lastUpdateCheck")), StandardCharsets.UTF_8);
    assertThat(testWebServer.getInputRead()).contains("User-Agent: jib 2.0.0 tool-name");
    assertThat(Instant.parse(modifiedTime)).isGreaterThan(before);
  }

  @Test
  void testPerformUpdateCheck_noLastUpdateCheck() throws IOException, InterruptedException {
    Instant before = Instant.now();
    Thread.sleep(100);
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            configDir, "1.0.2", testWebServer.getEndpoint(), "tool-name", ignored -> {});

    assertThat(message).hasValue("2.0.0");
    String modifiedTime =
        new String(
            Files.readAllBytes(configDir.resolve("lastUpdateCheck")), StandardCharsets.UTF_8);
    assertThat(Instant.parse(modifiedTime)).isGreaterThan(before);
  }

  @Test
  void testPerformUpdateCheck_emptyLastUpdateCheck() throws IOException, InterruptedException {
    Files.createFile(configDir.resolve("lastUpdateCheck"));
    Instant before = Instant.now();
    Thread.sleep(100);
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            configDir, "1.0.2", testWebServer.getEndpoint(), "tool-name", ignored -> {});

    assertThat(message).hasValue("2.0.0");
    String modifiedTime =
        new String(
            Files.readAllBytes(configDir.resolve("lastUpdateCheck")), StandardCharsets.UTF_8);
    assertThat(Instant.parse(modifiedTime)).isGreaterThan(before);
  }

  @Test
  void testPerformUpdateCheck_lastUpdateCheckTooSoon() throws IOException {
    FileTime modifiedTime = FileTime.from(Instant.now().minusSeconds(12));
    setupLastUpdateCheck();
    Files.write(
        configDir.resolve("lastUpdateCheck"),
        modifiedTime.toString().getBytes(StandardCharsets.UTF_8));
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            configDir, "1.0.2", testWebServer.getEndpoint(), "tool-name", ignored -> {});

    assertThat(message).isEmpty();

    // lastUpdateCheck should not have changed
    String lastUpdateTime =
        new String(
            Files.readAllBytes(configDir.resolve("lastUpdateCheck")), StandardCharsets.UTF_8);
    assertThat(modifiedTime.toInstant()).isEqualTo(Instant.parse(lastUpdateTime));
  }

  @Test
  void testPerformUpdateCheck_badLastUpdateTime() throws IOException, InterruptedException {
    Instant before = Instant.now();
    Thread.sleep(100);
    Files.write(
        configDir.resolve("lastUpdateCheck"), "bad timestamp".getBytes(StandardCharsets.UTF_8));
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            configDir, "1.0.2", testWebServer.getEndpoint(), "tool-name", ignored -> {});
    String modifiedTime =
        new String(
            Files.readAllBytes(configDir.resolve("lastUpdateCheck")), StandardCharsets.UTF_8);

    assertThat(Instant.parse(modifiedTime)).isGreaterThan(before);
    assertThat(message).hasValue("2.0.0");
  }

  @Test
  void testPerformUpdateCheck_failSilently()
      throws InterruptedException, GeneralSecurityException, URISyntaxException, IOException {
    String response = "HTTP/1.1 400 Bad Request\nContent-Length: 0\n\n";
    try (TestWebServer badServer =
        new TestWebServer(false, Collections.singletonList(response), 1)) {
      Optional<String> message =
          UpdateChecker.performUpdateCheck(
              configDir,
              "1.0.2",
              badServer.getEndpoint(),
              "tool-name",
              logEvent -> {
                assertThat(logEvent.getLevel()).isEqualTo(LogEvent.Level.DEBUG);
                assertThat(logEvent.getMessage()).contains("Update check failed; ");
              });
      assertThat(message).isEmpty();
    }
  }

  @Test
  void testFinishUpdateCheck_success() {
    Future<Optional<String>> updateCheckFuture = Futures.immediateFuture(Optional.of("Hello"));
    Optional<String> result = UpdateChecker.finishUpdateCheck(updateCheckFuture);
    assertThat(result).hasValue("Hello");
  }

  @Test
  void testFinishUpdateCheck_notDone() {
    @SuppressWarnings("unchecked")
    Future<Optional<String>> updateCheckFuture = Mockito.mock(Future.class);
    Mockito.when(updateCheckFuture.isDone()).thenReturn(false);

    Optional<String> result = UpdateChecker.finishUpdateCheck(updateCheckFuture);
    assertThat(result).isEmpty();
  }

  private void setupLastUpdateCheck() throws IOException {
    Files.write(
        configDir.resolve("lastUpdateCheck"),
        Instant.now().minus(Duration.ofDays(2)).toString().getBytes(StandardCharsets.UTF_8));
  }
}
