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

import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.builder.steps.BuildResult;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.common.collect.ImmutableSet;
import java.security.DigestException;
import java.util.Set;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/** Tests for {@link JibContainer}. */
class JibContainerTest {

  @Rule public TemporaryFolder temporaryDirectory = new TemporaryFolder();

  private ImageReference targetImage1;
  private ImageReference targetImage2;
  private DescriptorDigest digest1;
  private DescriptorDigest digest2;
  private Set<String> tags1;
  private Set<String> tags2;

  @BeforeEach
  void setUp() throws DigestException, InvalidImageReferenceException {
    targetImage1 = ImageReference.parse("gcr.io/project/image:tag");
    targetImage2 = ImageReference.parse("gcr.io/project/image:tag2");
    digest1 =
        DescriptorDigest.fromDigest(
            "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
    digest2 =
        DescriptorDigest.fromDigest(
            "sha256:9876543210fedcba9876543210fedcba9876543210fedcba9876543210fedcba");
    tags1 = ImmutableSet.of("latest", "custom-tag");
    tags2 = ImmutableSet.of("latest");
  }

  @Test
  void testCreation() {
    JibContainer container = new JibContainer(targetImage1, digest1, digest2, tags1, true);

    Assert.assertEquals(targetImage1, container.getTargetImage());
    Assert.assertEquals(digest1, container.getDigest());
    Assert.assertEquals(digest2, container.getImageId());
    Assert.assertEquals(tags1, container.getTags());
    Assert.assertTrue(container.isImagePushed());
  }

  @Test
  void testEquality() {
    JibContainer container1 = new JibContainer(targetImage1, digest1, digest2, tags1, true);
    JibContainer container2 = new JibContainer(targetImage1, digest1, digest2, tags1, true);

    Assert.assertEquals(container1, container2);
    Assert.assertEquals(container1.hashCode(), container2.hashCode());
  }

  @Test
  void testEquality_differentTargetImage() {
    JibContainer container1 = new JibContainer(targetImage1, digest1, digest2, tags1, true);
    JibContainer container2 = new JibContainer(targetImage2, digest1, digest2, tags1, true);

    Assert.assertNotEquals(container1, container2);
    Assert.assertNotEquals(container1.hashCode(), container2.hashCode());
  }

  @Test
  void testEquality_differentImageDigest() {
    JibContainer container1 = new JibContainer(targetImage1, digest1, digest2, tags1, true);
    JibContainer container2 = new JibContainer(targetImage1, digest2, digest2, tags1, true);

    Assert.assertNotEquals(container1, container2);
    Assert.assertNotEquals(container1.hashCode(), container2.hashCode());
  }

  @Test
  void testEquality_differentImageId() {
    JibContainer container1 = new JibContainer(targetImage1, digest1, digest1, tags1, true);
    JibContainer container2 = new JibContainer(targetImage1, digest1, digest2, tags1, true);

    Assert.assertNotEquals(container1, container2);
    Assert.assertNotEquals(container1.hashCode(), container2.hashCode());
  }

  @Test
  void testEquality_differentTags() {
    JibContainer container1 = new JibContainer(targetImage1, digest1, digest1, tags1, true);
    JibContainer container2 = new JibContainer(targetImage1, digest1, digest1, tags2, true);

    Assert.assertNotEquals(container1, container2);
    Assert.assertNotEquals(container1.hashCode(), container2.hashCode());
  }

  @Test
  void testEquality_differentImagePushed() {
    JibContainer container1 = new JibContainer(targetImage1, digest1, digest1, tags1, true);
    JibContainer container2 = new JibContainer(targetImage1, digest1, digest1, tags1, false);

    Assert.assertNotEquals(container1, container2);
    Assert.assertNotEquals(container1.hashCode(), container2.hashCode());
  }

  @Test
  void testCreation_withBuildContextAndBuildResult() {
    BuildResult buildResult = Mockito.mock(BuildResult.class);
    BuildContext buildContext = Mockito.mock(BuildContext.class);
    ImageConfiguration mockTargetConfiguration = Mockito.mock(ImageConfiguration.class);

    when(buildResult.getImageDigest()).thenReturn(digest1);
    when(buildResult.getImageId()).thenReturn(digest1);
    when(buildResult.isImagePushed()).thenReturn(true);
    when(mockTargetConfiguration.getImage()).thenReturn(targetImage1);
    when(buildContext.getTargetImageConfiguration()).thenReturn(mockTargetConfiguration);
    when(buildContext.getAllTargetImageTags()).thenReturn(ImmutableSet.copyOf(tags1));

    JibContainer container = JibContainer.from(buildContext, buildResult);
    Assert.assertEquals(targetImage1, container.getTargetImage());
    Assert.assertEquals(digest1, container.getDigest());
    Assert.assertEquals(digest1, container.getImageId());
    Assert.assertEquals(tags1, container.getTags());
    Assert.assertTrue(container.isImagePushed());
  }
}
