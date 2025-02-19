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
import static org.junit.Assert.assertThrows;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.cli.logging.HttpTraceLevel;
import com.google.cloud.tools.jib.cli.logging.Verbosity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Paths;
import java.util.stream.Stream;
import junitparams.JUnitParamsRunner;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runner.RunWith;
import picocli.CommandLine;
import picocli.CommandLine.MissingParameterException;

@RunWith(JUnitParamsRunner.class)
class BuildTest {
  @Test
  void testParse_missingRequiredParams() {
    MissingParameterException mpe =
        assertThrows(
            MissingParameterException.class, () -> CommandLine.populateCommand(new Build(), ""));
    assertThat(mpe.getMessage()).isEqualTo("Missing required option: '--target=<target-image>'");
  }

  @Test
  void testParse_defaults() {
    Build buildCommand = CommandLine.populateCommand(new Build(), "-t", "test-image-ref");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;
    assertThat(commonCliOptions.getTargetImage()).isEqualTo("test-image-ref");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(buildCommand.buildFileUnprocessed).isNull();
    assertThat(buildCommand.getBuildFile()).isEqualTo(Paths.get("./jib.yaml"));
    assertThat(buildCommand.contextRoot).isEqualTo(Paths.get("."));
    assertThat(commonCliOptions.getAdditionalTags()).isEmpty();
    assertThat(buildCommand.getTemplateParameters()).isEmpty();
    assertThat(commonCliOptions.getProjectCache()).isEmpty();
    assertThat(commonCliOptions.getBaseImageCache()).isEmpty();
    assertThat(commonCliOptions.isAllowInsecureRegistries()).isFalse();
    assertThat(commonCliOptions.isSendCredentialsOverHttp()).isFalse();
    assertThat(commonCliOptions.getVerbosity()).isEqualTo(Verbosity.lifecycle);
    assertThat(commonCliOptions.isStacktrace()).isFalse();
    assertThat(commonCliOptions.getHttpTrace()).isEqualTo(HttpTraceLevel.off);
    assertThat(commonCliOptions.isSerialize()).isFalse();
    assertThat(commonCliOptions.getImageJsonPath()).isEmpty();
  }

  @Test
  void testParse_shortFormParams() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(),
            "-t=test-image-ref",
            "-c=test-context",
            "-b=test-build-file",
            "-p=param1=value1",
            "-p=param2=value2");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;
    assertThat(commonCliOptions.getTargetImage()).isEqualTo("test-image-ref");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(buildCommand.buildFileUnprocessed).isEqualTo(Paths.get("test-build-file"));
    assertThat(buildCommand.getBuildFile()).isEqualTo(Paths.get("test-build-file"));
    assertThat(buildCommand.contextRoot).isEqualTo(Paths.get("test-context"));
    assertThat(commonCliOptions.getAdditionalTags()).isEmpty();
    assertThat(buildCommand.getTemplateParameters())
        .isEqualTo(ImmutableMap.of("param1", "value1", "param2", "value2"));
    assertThat(commonCliOptions.getProjectCache()).isEmpty();
    assertThat(commonCliOptions.getBaseImageCache()).isEmpty();
    assertThat(commonCliOptions.isAllowInsecureRegistries()).isFalse();
    assertThat(commonCliOptions.isSendCredentialsOverHttp()).isFalse();
    assertThat(commonCliOptions.getVerbosity()).isEqualTo(Verbosity.lifecycle);
    assertThat(commonCliOptions.isStacktrace()).isFalse();
    assertThat(commonCliOptions.isStacktrace()).isFalse();
    assertThat(commonCliOptions.getHttpTrace()).isEqualTo(HttpTraceLevel.off);
    assertThat(commonCliOptions.isSerialize()).isFalse();
    assertThat(commonCliOptions.getImageJsonPath()).isEmpty();
  }

  @Test
  void testParse_longFormParams() {
    // this test does not check credential helpers, scroll down for specialized credential helper
    // tests
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(),
            "--target=test-image-ref",
            "--context=test-context",
            "--build-file=test-build-file",
            "--parameter=param1=value1",
            "--parameter=param2=value2",
            "--additional-tags=tag1,tag2,tag3",
            "--allow-insecure-registries",
            "--send-credentials-over-http",
            "--project-cache=test-project-cache",
            "--base-image-cache=test-base-image-cache",
            "--verbosity=info",
            "--stacktrace",
            "--http-trace",
            "--serialize",
            "--image-metadata-out=path/to/json/jib-image.json");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;
    assertThat(commonCliOptions.getTargetImage()).isEqualTo("test-image-ref");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(buildCommand.buildFileUnprocessed).isEqualTo(Paths.get("test-build-file"));
    assertThat(buildCommand.getBuildFile()).isEqualTo(Paths.get("test-build-file"));
    assertThat(buildCommand.contextRoot).isEqualTo(Paths.get("test-context"));
    assertThat(commonCliOptions.getAdditionalTags())
        .isEqualTo(ImmutableList.of("tag1", "tag2", "tag3"));
    assertThat(buildCommand.getTemplateParameters())
        .isEqualTo(ImmutableMap.of("param1", "value1", "param2", "value2"));
    assertThat(commonCliOptions.getProjectCache()).hasValue(Paths.get("test-project-cache"));
    assertThat(commonCliOptions.getBaseImageCache()).hasValue(Paths.get("test-base-image-cache"));
    assertThat(commonCliOptions.isAllowInsecureRegistries()).isTrue();
    assertThat(commonCliOptions.isSendCredentialsOverHttp()).isTrue();
    assertThat(commonCliOptions.getVerbosity()).isEqualTo(Verbosity.info);
    assertThat(commonCliOptions.isStacktrace()).isTrue();
    assertThat(commonCliOptions.getHttpTrace()).isEqualTo(HttpTraceLevel.config);
    assertThat(commonCliOptions.isSerialize()).isTrue();
    assertThat(commonCliOptions.getImageJsonPath())
        .hasValue(Paths.get("path/to/json/jib-image.json"));
  }

  @Test
  void testParse_buildFileDefaultForContext() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(), "--target", "test-image-ref", "--context", "test-context");
    assertThat(buildCommand.buildFileUnprocessed).isNull();
    assertThat(buildCommand.getBuildFile()).isEqualTo(Paths.get("test-context/jib.yaml"));
    assertThat(buildCommand.contextRoot).isEqualTo(Paths.get("test-context"));
  }

  @Test
  void testParse_credentialHelper() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(), "--target=test-image-ref", "--credential-helper=test-cred-helper");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;
    assertThat(commonCliOptions.getCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  void testParse_toCredentialHelper() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(), "--target=test-image-ref", "--to-credential-helper=test-cred-helper");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  void testParse_fromCredentialHelper() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(), "--target=test-image-ref", "--from-credential-helper=test-cred-helper");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  void testParse_usernamePassword() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(),
            "--target=test-image-ref",
            "--username=test-username",
            "--password=test-password");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  void testParse_toUsernamePassword() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(),
            "--target=test-image-ref",
            "--to-username=test-username",
            "--to-password=test-password");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;
    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  void testParse_fromUsernamePassword() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(),
            "--target=test-image-ref",
            "--from-username=test-username",
            "--from-password=test-password");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
  }

  @Test
  void testParse_toAndFromUsernamePassword() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(),
            "--target=test-image-ref",
            "--to-username=test-username-1",
            "--to-password=test-password-1",
            "--from-username=test-username-2",
            "--from-password=test-password-2");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword())
        .hasValue(Credential.from("test-username-1", "test-password-1"));
    assertThat(commonCliOptions.getFromUsernamePassword())
        .hasValue(Credential.from("test-username-2", "test-password-2"));
  }

  @Test
  void testParse_toAndFromCredentialHelper() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(),
            "--target=test-image-ref",
            "--to-credential-helper=to-test-helper",
            "--from-credential-helper=from-test-helper");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).hasValue("to-test-helper");
    assertThat(commonCliOptions.getFromCredentialHelper()).hasValue("from-test-helper");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  void testParse_toUsernamePasswordAndFromCredentialHelper() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(),
            "--target=test-image-ref",
            "--to-username=test-username",
            "--to-password=test-password",
            "--from-credential-helper=test-cred-helper");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  void testParse_toCredentialHelperAndFromUsernamePassword() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(),
            "--target=test-image-ref",
            "--to-credential-helper=test-cred-helper",
            "--from-username=test-username",
            "--from-password=test-password");
    CommonCliOptions commonCliOptions = buildCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
  }

  private static Stream<Arguments> usernamePasswordPairs() {
    return Stream.of(
        Arguments.of("--username", "--password"),
        Arguments.of("--to-username", "--to-password"),
        Arguments.of("--from-username", "--from-password"));
  }

  @ParameterizedTest
  @MethodSource("usernamePasswordPairs")
  void testParse_usernameWithoutPassword(String usernameField, String passwordField) {
    MissingParameterException mpe =
        assertThrows(
            MissingParameterException.class,
            () ->
                CommandLine.populateCommand(
                    new Build(), "--target", "test-image-ref", usernameField, "test-username"));
    assertThat(mpe.getMessage()).isEqualTo("Error: Missing required argument(s): " + passwordField);
  }

  @ParameterizedTest
  @MethodSource("usernamePasswordPairs")
  void testParse_passwordWithoutUsername(String usernameField, String passwordField) {
    MissingParameterException mpe =
        assertThrows(
            MissingParameterException.class,
            () ->
                CommandLine.populateCommand(
                    new Build(), "--target", "test-image-ref", passwordField, "test-password"));
    assertThat(mpe.getMessage())
        .isEqualTo("Error: Missing required argument(s): " + usernameField + "=<username>");
  }

  public static Stream<Arguments> incompatibleCredentialOptions() {
    return Stream.of(
        Arguments.of((Object) new String[] {"--credential-helper=x", "--to-credential-helper=x"}),
        Arguments.of((Object) new String[] {"--credential-helper=x", "--from-credential-helper=x"}),
        Arguments.of(
            (Object) new String[] {"--credential-helper=x", "--username=x", "--password=x"}),
        Arguments.of(
            (Object)
                new String[] {"--credential-helper=x", "--from-username=x", "--from-password=x"}),
        Arguments.of(
            (Object) new String[] {"--credential-helper=x", "--to-username=x", "--to-password=x"}),
        Arguments.of(
            (Object)
                new String[] {
                  "--username=x", "--password=x", "--from-username=x", "--from-password=x"
                }),
        Arguments.of(
            (Object)
                new String[] {
                  "--username=x", "--password=x", "--to-username=x", "--to-password=x"
                }),
        Arguments.of(
            (Object) new String[] {"--username=x", "--password=x", "--to-credential-helper=x"}),
        Arguments.of(
            (Object) new String[] {"--username=x", "--password=x", "--from-credential-helper=x"}),
        Arguments.of(
            (Object)
                new String[] {
                  "--from-credential-helper=x", "--from-username=x", "--from-password=x"
                }),
        Arguments.of(
            (Object)
                new String[] {"--to-credential-helper=x", "--to-password=x", "--to-username=x"}));
  }

  @ParameterizedTest
  @MethodSource("incompatibleCredentialOptions")
  void testParse_incompatibleCredentialOptions(String[] authArgs) {
    CommandLine.MutuallyExclusiveArgsException meae =
        assertThrows(
            CommandLine.MutuallyExclusiveArgsException.class,
            () ->
                CommandLine.populateCommand(
                    new Build(), ArrayUtils.add(authArgs, "--target=ignored")));
    assertThat(meae)
        .hasMessageThat()
        .containsMatch("^Error: (\\[)*(--(from-|to-)?credential-helper|\\[--(username|password))");
  }

  @Test
  void testValidate_nameMissingFail() {
    Build buildCommand = CommandLine.populateCommand(new Build(), "--target=tar://sometar.tar");
    CommandLine.ParameterException pex =
        assertThrows(CommandLine.ParameterException.class, buildCommand.commonCliOptions::validate);
    assertThat(pex.getMessage())
        .isEqualTo("Missing option: --name must be specified when using --target=tar://....");
  }

  @Test
  void testValidate_pass() {
    Build buildCommand =
        CommandLine.populateCommand(
            new Build(), "--target=tar://sometar.tar", "--name=test.io/test/test");
    buildCommand.commonCliOptions.validate();
    // pass
  }
}
