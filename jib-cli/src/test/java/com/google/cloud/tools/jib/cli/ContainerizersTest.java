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

package com.google.cloud.tools.jib.cli;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.ContainerizerTestProxy;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.common.collect.ImmutableSet;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContainerizersTest {

  @TempDir public Path temporaryFolder;
  @Rule public final MockitoRule mockitoRule = MockitoJUnit.rule();
  // Containerizers will add system properties based on cli properties
  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

  @Mock private ConsoleLogger consoleLogger;
  @Mock private CacheDirectories cacheDirectories;

  private static final Path baseImageCache = Paths.get("base-image-cache-for-test");
  private static final Path applicationCache = Paths.get("application-cache-for-test");

  @BeforeEach
  void initCaches() {
    Mockito.when(cacheDirectories.getBaseImageCache()).thenReturn(Optional.of(baseImageCache));
    Mockito.when(cacheDirectories.getApplicationLayersCache()).thenReturn(applicationCache);
  }

  @Test
  void testApplyConfiguration_defaults()
      throws InvalidImageReferenceException, FileNotFoundException,
          CacheDirectoryCreationException {
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(new CommonCliOptions(), "-t", "test-image-ref");
    ContainerizerTestProxy containerizer =
        new ContainerizerTestProxy(
            Containerizers.from(commonCliOptions, consoleLogger, cacheDirectories));

    assertThat(Boolean.getBoolean(JibSystemProperties.SEND_CREDENTIALS_OVER_HTTP)).isFalse();
    assertThat(Boolean.getBoolean(JibSystemProperties.SERIALIZE)).isFalse();
    assertThat(containerizer.getToolName()).isEqualTo(VersionInfo.TOOL_NAME);
    assertThat(containerizer.getToolVersion()).isEqualTo(VersionInfo.getVersionSimple());
    assertThat(Boolean.getBoolean("sendCredentialsOverHttp")).isFalse();
    assertThat(containerizer.getAllowInsecureRegistries()).isFalse();
    assertThat(containerizer.getBaseImageLayersCacheDirectory()).isEqualTo(baseImageCache);
    assertThat(containerizer.getApplicationsLayersCacheDirectory()).isEqualTo(applicationCache);
    assertThat(containerizer.getAdditionalTags()).isEqualTo(ImmutableSet.of());
  }

  @Test
  void testApplyConfiguration_withValues()
      throws InvalidImageReferenceException, CacheDirectoryCreationException,
          FileNotFoundException {
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(
            new CommonCliOptions(),
            "-t=test-image-ref",
            "--send-credentials-over-http",
            "--allow-insecure-registries",
            "--additional-tags=tag1,tag2",
            "--serialize");
    ContainerizerTestProxy containerizer =
        new ContainerizerTestProxy(
            Containerizers.from(commonCliOptions, consoleLogger, cacheDirectories));

    assertThat(Boolean.getBoolean(JibSystemProperties.SEND_CREDENTIALS_OVER_HTTP)).isTrue();
    assertThat(Boolean.getBoolean(JibSystemProperties.SERIALIZE)).isTrue();
    assertThat(containerizer.getAllowInsecureRegistries()).isTrue();
    assertThat(containerizer.getBaseImageLayersCacheDirectory()).isEqualTo(baseImageCache);
    assertThat(containerizer.getApplicationsLayersCacheDirectory()).isEqualTo(applicationCache);
    assertThat(containerizer.getAdditionalTags()).isEqualTo(ImmutableSet.of("tag1", "tag2"));
  }

  @Test
  void testFrom_dockerDaemonImage() throws InvalidImageReferenceException, FileNotFoundException {
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(
            new CommonCliOptions(), "-t", "docker://gcr.io/test/test-image-ref");
    ContainerizerTestProxy containerizer =
        new ContainerizerTestProxy(
            Containerizers.from(commonCliOptions, consoleLogger, cacheDirectories));

    assertThat(containerizer.getDescription()).isEqualTo("Building image to Docker daemon");
    ImageConfiguration config = containerizer.getImageConfiguration();

    assertThat(config.getCredentialRetrievers()).isEmpty();
    assertThat(config.getDockerClient()).isEmpty();
    assertThat(config.getImage().toString()).isEqualTo("gcr.io/test/test-image-ref");
    assertThat(config.getTarPath()).isEmpty();
  }

  @Test
  void testFrom_tarImage() throws InvalidImageReferenceException, IOException {
    Path tarPath = temporaryFolder.resolve("test-tar.tar");
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(
            new CommonCliOptions(),
            "-t=tar://" + tarPath.toAbsolutePath(),
            "--name=gcr.io/test/test-image-ref");
    ContainerizerTestProxy containerizer =
        new ContainerizerTestProxy(
            Containerizers.from(commonCliOptions, consoleLogger, cacheDirectories));

    assertThat(containerizer.getDescription()).isEqualTo("Building image tarball");
    ImageConfiguration config = containerizer.getImageConfiguration();

    assertThat(config.getCredentialRetrievers()).isEmpty();
    assertThat(config.getDockerClient()).isEmpty();
    assertThat(config.getImage().toString()).isEqualTo("gcr.io/test/test-image-ref");
    assertThat(config.getTarPath()).isEmpty(); // weird, but the way jib currently works
  }

  @Test
  void testFrom_registryImage() throws InvalidImageReferenceException, IOException {
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(
            new CommonCliOptions(), "-t", "registry://gcr.io/test/test-image-ref");
    ContainerizerTestProxy containerizer =
        new ContainerizerTestProxy(
            Containerizers.from(commonCliOptions, consoleLogger, cacheDirectories));

    // description from Containerizer.java
    assertThat(containerizer.getDescription()).isEqualTo("Building and pushing image");
    ImageConfiguration config = containerizer.getImageConfiguration();

    assertThat(config.getCredentialRetrievers()).isNotEmpty();
    assertThat(config.getDockerClient()).isEmpty();
    assertThat(config.getImage().toString()).isEqualTo("gcr.io/test/test-image-ref");
    assertThat(config.getTarPath()).isEmpty();
  }
}
