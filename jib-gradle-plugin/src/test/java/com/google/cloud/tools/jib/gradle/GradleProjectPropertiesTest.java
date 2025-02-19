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

package com.google.cloud.tools.jib.gradle;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JavaContainerBuilder.LayerType;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilderTestHelper;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.plugins.common.ContainerizingMode;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import com.google.common.truth.Correspondence;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.bundling.War;
import org.gradle.jvm.tasks.Jar;
import org.gradle.testfixtures.ProjectBuilder;
import org.hamcrest.CoreMatchers;
import org.junit.Assume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Test for {@link GradleProjectProperties}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GradleProjectPropertiesTest {

  private static final Correspondence<FileEntry, Path> SOURCE_FILE_OF =
      Correspondence.transforming(FileEntry::getSourceFile, "has sourceFile of");
  private static final Correspondence<FileEntry, String> EXTRACTION_PATH_OF =
      Correspondence.transforming(
          entry -> entry.getExtractionPath().toString(), "has extractionPath of");
  private static final Correspondence<File, Path> FILE_PATH_OF =
      Correspondence.transforming(File::toPath, "has Path of");

  private static final Instant EPOCH_PLUS_32 = Instant.ofEpochSecond(32);

  /** Helper for reading back layers in a {@link BuildContext}. */
  private static class ContainerBuilderLayers {

    private final FileEntriesLayer resourcesLayer;
    private final FileEntriesLayer classesLayer;
    private final FileEntriesLayer dependenciesLayer;
    private final FileEntriesLayer snapshotsLayer;

    private ContainerBuilderLayers(BuildContext buildContext) {
      resourcesLayer = getLayerByName(buildContext, LayerType.RESOURCES.getName());
      classesLayer = getLayerByName(buildContext, LayerType.CLASSES.getName());
      dependenciesLayer = getLayerByName(buildContext, LayerType.DEPENDENCIES.getName());
      snapshotsLayer = getLayerByName(buildContext, LayerType.SNAPSHOT_DEPENDENCIES.getName());
    }

    private static FileEntriesLayer getLayerByName(BuildContext buildContext, String name) {
      List<FileEntriesLayer> layers = buildContext.getLayerConfigurations();
      return layers.stream().filter(layer -> layer.getName().equals(name)).findFirst().get();
    }
  }

  private static Path getResource(String path) throws URISyntaxException {
    return Paths.get(Resources.getResource(path).toURI());
  }

  @TempDir public Path temporaryFolder;

  @Mock private TempDirectoryProvider mockTempDirectoryProvider;
  @Mock private Supplier<List<JibGradlePluginExtension<?>>> mockExtensionLoader;
  @Mock private Logger mockLogger;

  private GradleProjectProperties gradleProjectProperties;
  private Project project;

  @BeforeEach
  void setUp() throws URISyntaxException, IOException {
    when(mockLogger.isDebugEnabled()).thenReturn(true);
    when(mockLogger.isInfoEnabled()).thenReturn(true);
    when(mockLogger.isWarnEnabled()).thenReturn(true);
    when(mockLogger.isErrorEnabled()).thenReturn(true);

    Path tmpDir = Files.createTempDirectory(temporaryFolder, "jib");

    Path projectDir = getResource("gradle/application");
    project =
        ProjectBuilder.builder()
            .withName("my-app")
            .withProjectDir(projectDir.toFile())
            .withGradleUserHomeDir(tmpDir.toFile())
            .build();

    project.getRepositories().add(project.getRepositories().mavenCentral());

    project.getPlugins().apply("java");
    DependencyHandler dependencies = project.getDependencies();

    dependencies.add(
        "implementation",
        project.files(
            "dependencies/library.jarC.jar",
            "dependencies/libraryB.jar",
            "dependencies/libraryA.jar",
            "dependencies/dependency-1.0.0.jar",
            "dependencies/more/dependency-1.0.0.jar",
            "dependencies/another/one/dependency-1.0.0.jar",
            "dependencies/dependencyX-1.0.0-SNAPSHOT.jar"));

    // We can't commit an empty directory in Git, so create (if not exist).
    Path emptyDirectory = getResource("gradle/webapp").resolve("WEB-INF/classes/empty_dir");
    Files.createDirectories(emptyDirectory);
  }

  @Test
  void testGetMainClassFromJar_success() {

    Jar jar = project.getTasks().withType(Jar.class).getByName("jar");
    jar.setManifest(
        new DefaultManifest(null).attributes(ImmutableMap.of("Main-Class", "some.main.class")));

    setupGradleProjectPropertiesInstance();
    assertThat(gradleProjectProperties.getMainClassFromJarPlugin()).isEqualTo("some.main.class");
  }

  @Test
  void testGetMainClassFromJar_missing() {
    setupGradleProjectPropertiesInstance();

    assertThat(gradleProjectProperties.getMainClassFromJarPlugin()).isNull();
  }

  @Test
  void testGetMainClassFromJarAsProperty_success() {

    Property<String> mainClass =
        project.getObjects().property(String.class).value("some.main.class");

    Jar jar = project.getTasks().withType(Jar.class).getByName("jar");
    jar.setManifest(new DefaultManifest(null).attributes(ImmutableMap.of("Main-Class", mainClass)));

    setupGradleProjectPropertiesInstance();

    assertThat(gradleProjectProperties.getMainClassFromJarPlugin()).isEqualTo("some.main.class");
  }

  @Test
  void testGetMainClassFromJarAsPropertyWithValueNull_missing() {

    Property<String> mainClass = project.getObjects().property(String.class).value((String) null);

    Jar jar = project.getTasks().withType(Jar.class).getByName("jar");
    jar.setManifest(new DefaultManifest(null).attributes(ImmutableMap.of("Main-Class", mainClass)));
    setupGradleProjectPropertiesInstance();
    assertThat(gradleProjectProperties.getMainClassFromJarPlugin()).isNull();
  }

  @Test
  void testIsWarProject() {
    project.getPlugins().apply("war");
    setupGradleProjectPropertiesInstance();

    assertThat(gradleProjectProperties.isWarProject()).isTrue();
  }

  @Test
  void testGetInputFiles() throws URISyntaxException {
    Path applicationDirectory = getResource("gradle/application");

    List<Path> extraDirectories = Arrays.asList(applicationDirectory.resolve("extra-directory"));
    FileCollection fileCollection =
        GradleProjectProperties.getInputFiles(
            project, extraDirectories, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);

    assertThat(fileCollection)
        .comparingElementsUsing(FILE_PATH_OF)
        .containsExactly(
            applicationDirectory.resolve("build/classes/java/main"),
            applicationDirectory.resolve("build/resources/main"),
            applicationDirectory.resolve("dependencies/dependencyX-1.0.0-SNAPSHOT.jar"),
            applicationDirectory.resolve("dependencies/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/more/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/another/one/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/libraryA.jar"),
            applicationDirectory.resolve("dependencies/libraryB.jar"),
            applicationDirectory.resolve("dependencies/library.jarC.jar"),
            applicationDirectory.resolve("extra-directory"));
  }

  @Test
  void testConvertPermissionsMap() {
    Map<String, String> map = ImmutableMap.of("/test/folder/file1", "123", "/test/file2", "456");
    assertThat(TaskCommon.convertPermissionsMap(map))
        .containsExactly(
            "/test/folder/file1",
            FilePermissions.fromOctalString("123"),
            "/test/file2",
            FilePermissions.fromOctalString("456"))
        .inOrder();

    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> TaskCommon.convertPermissionsMap(ImmutableMap.of("path", "invalid permission")));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("octalPermissions must be a 3-digit octal number (000-777)");
  }

  @Test
  void testGetMajorJavaVersion() {
    JavaPluginExtension convention = project.getExtensions().findByType(JavaPluginExtension.class);

    convention.setTargetCompatibility(JavaVersion.VERSION_1_3);
    setupGradleProjectPropertiesInstance();

    assertThat(gradleProjectProperties.getMajorJavaVersion()).isEqualTo(3);

    convention.setTargetCompatibility(JavaVersion.VERSION_11);
    setupGradleProjectPropertiesInstance();
    assertThat(gradleProjectProperties.getMajorJavaVersion()).isEqualTo(11);

    convention.setTargetCompatibility(JavaVersion.VERSION_1_9);
    setupGradleProjectPropertiesInstance();
    assertThat(gradleProjectProperties.getMajorJavaVersion()).isEqualTo(9);
  }

  @Test
  void testGetMajorJavaVersion_jvm8() {
    Assume.assumeThat(JavaVersion.current(), CoreMatchers.is(JavaVersion.VERSION_1_8));
    setupGradleProjectPropertiesInstance();
    assertThat(gradleProjectProperties.getMajorJavaVersion()).isEqualTo(8);
  }

  @Test
  void testGetMajorJavaVersion_jvm11() {
    Assume.assumeThat(JavaVersion.current(), CoreMatchers.is(JavaVersion.VERSION_11));
    setupGradleProjectPropertiesInstance();
    assertThat(gradleProjectProperties.getMajorJavaVersion()).isEqualTo(11);
  }

  @Test
  void testCreateContainerBuilder_correctSourceFiles()
      throws URISyntaxException, InvalidImageReferenceException, CacheDirectoryCreationException {
    setupGradleProjectPropertiesInstance();

    ContainerBuilderLayers layers = new ContainerBuilderLayers(setupBuildContext());

    Path applicationDirectory = getResource("gradle/application");
    assertThat(layers.snapshotsLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            applicationDirectory.resolve("dependencies/dependencyX-1.0.0-SNAPSHOT.jar"));
    assertThat(layers.dependenciesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            applicationDirectory.resolve("dependencies/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/more/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/another/one/dependency-1.0.0.jar"),
            applicationDirectory.resolve("dependencies/libraryA.jar"),
            applicationDirectory.resolve("dependencies/libraryB.jar"),
            applicationDirectory.resolve("dependencies/library.jarC.jar"));
    assertThat(layers.resourcesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            applicationDirectory.resolve("build/resources/main/resourceA"),
            applicationDirectory.resolve("build/resources/main/resourceB"),
            applicationDirectory.resolve("build/resources/main/world"));
    assertThat(layers.classesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            applicationDirectory.resolve("build/classes/java/main/HelloWorld.class"),
            applicationDirectory.resolve("build/classes/java/main/some.class"));

    List<FileEntry> allFileEntries = new ArrayList<>();
    allFileEntries.addAll(layers.snapshotsLayer.getEntries());
    allFileEntries.addAll(layers.dependenciesLayer.getEntries());
    allFileEntries.addAll(layers.classesLayer.getEntries());
    allFileEntries.addAll(layers.resourcesLayer.getEntries());
    Set<Instant> modificationTimes =
        allFileEntries.stream().map(FileEntry::getModificationTime).collect(Collectors.toSet());
    assertThat(modificationTimes).containsExactly(EPOCH_PLUS_32);
  }

  @Test
  void testCreateContainerBuilder_noClassesFiles()
      throws InvalidImageReferenceException, IOException {
    Path tmpDir = Files.createTempDirectory(temporaryFolder, "jib");
    Path tmpDir2 = Files.createTempDirectory(temporaryFolder, "jib");

    Project project =
        ProjectBuilder.builder()
            .withProjectDir(tmpDir.toFile())
            .withGradleUserHomeDir(tmpDir2.toFile())
            .build();
    project.getPlugins().apply("java");

    gradleProjectProperties =
        new GradleProjectProperties(
            project,
            mockLogger,
            mockTempDirectoryProvider,
            mockExtensionLoader,
            JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);

    gradleProjectProperties.createJibContainerBuilder(
        JavaContainerBuilder.from(RegistryImage.named("base")), ContainerizingMode.EXPLODED);
    gradleProjectProperties.waitForLoggingThread();
    verify(mockLogger).warn("No classes files were found - did you compile your project?");
  }

  @Test
  void testCreateContainerBuilder_correctExtractionPaths()
      throws InvalidImageReferenceException, CacheDirectoryCreationException {
    setupGradleProjectPropertiesInstance();
    ContainerBuilderLayers layers = new ContainerBuilderLayers(setupBuildContext());

    assertThat(layers.dependenciesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/my/app/libs/dependency-1.0.0-770.jar",
            "/my/app/libs/dependency-1.0.0-200.jar",
            "/my/app/libs/dependency-1.0.0-480.jar",
            "/my/app/libs/libraryA.jar",
            "/my/app/libs/libraryB.jar",
            "/my/app/libs/library.jarC.jar");
    assertThat(layers.snapshotsLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/libs/dependencyX-1.0.0-SNAPSHOT.jar");
    assertThat(layers.resourcesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/my/app/resources/resourceA",
            "/my/app/resources/resourceB",
            "/my/app/resources/world");
    assertThat(layers.classesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/classes/HelloWorld.class", "/my/app/classes/some.class");
  }

  @Test
  void testCreateContainerBuilder_war()
      throws URISyntaxException, IOException, InvalidImageReferenceException,
          CacheDirectoryCreationException {

    Path unzipTarget = setUpWarProject(getResource("gradle/webapp"));
    setupGradleProjectPropertiesInstance();

    ContainerBuilderLayers layers = new ContainerBuilderLayers(setupBuildContext());
    assertThat(layers.dependenciesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(unzipTarget.resolve("WEB-INF/lib/dependency-1.0.0.jar"));
    assertThat(layers.snapshotsLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(unzipTarget.resolve("WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar"));
    assertThat(layers.resourcesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            unzipTarget.resolve("META-INF"),
            unzipTarget.resolve("META-INF/context.xml"),
            unzipTarget.resolve("Test.jsp"),
            unzipTarget.resolve("WEB-INF"),
            unzipTarget.resolve("WEB-INF/classes"),
            unzipTarget.resolve("WEB-INF/classes/empty_dir"),
            unzipTarget.resolve("WEB-INF/classes/package"),
            unzipTarget.resolve("WEB-INF/classes/package/test.properties"),
            unzipTarget.resolve("WEB-INF/lib"),
            unzipTarget.resolve("WEB-INF/web.xml"));
    assertThat(layers.classesLayer.getEntries())
        .comparingElementsUsing(SOURCE_FILE_OF)
        .containsExactly(
            unzipTarget.resolve("WEB-INF/classes/HelloWorld.class"),
            unzipTarget.resolve("WEB-INF/classes/empty_dir"),
            unzipTarget.resolve("WEB-INF/classes/package"),
            unzipTarget.resolve("WEB-INF/classes/package/Other.class"));

    assertThat(layers.dependenciesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/WEB-INF/lib/dependency-1.0.0.jar");
    assertThat(layers.snapshotsLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly("/my/app/WEB-INF/lib/dependencyX-1.0.0-SNAPSHOT.jar");
    assertThat(layers.resourcesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/my/app/META-INF",
            "/my/app/META-INF/context.xml",
            "/my/app/Test.jsp",
            "/my/app/WEB-INF",
            "/my/app/WEB-INF/classes",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/test.properties",
            "/my/app/WEB-INF/lib",
            "/my/app/WEB-INF/web.xml");
    assertThat(layers.classesLayer.getEntries())
        .comparingElementsUsing(EXTRACTION_PATH_OF)
        .containsExactly(
            "/my/app/WEB-INF/classes/HelloWorld.class",
            "/my/app/WEB-INF/classes/empty_dir",
            "/my/app/WEB-INF/classes/package",
            "/my/app/WEB-INF/classes/package/Other.class");
  }

  @Test
  void testCreateContainerBuilder_noErrorIfWebInfClassesDoesNotExist()
      throws IOException, InvalidImageReferenceException {

    File f = new File(temporaryFolder.toFile(), "WEB-INF");
    f.mkdirs();
    f = new File(temporaryFolder.toFile(), "lib");
    f.mkdirs();

    setUpWarProject(temporaryFolder);
    setupGradleProjectPropertiesInstance();

    assertThat(
            gradleProjectProperties.createJibContainerBuilder(
                JavaContainerBuilder.from("ignored"), ContainerizingMode.EXPLODED))
        .isNotNull();
  }

  @Test
  void testCreateContainerBuilder_noErrorIfWebInfLibDoesNotExist()
      throws IOException, InvalidImageReferenceException {
    File f = new File(temporaryFolder.toFile(), "WEB-INF");
    f.mkdirs();

    f = new File(temporaryFolder.toFile(), "classes");
    f.mkdirs();

    setUpWarProject(temporaryFolder);
    setupGradleProjectPropertiesInstance();

    assertThat(
            gradleProjectProperties.createJibContainerBuilder(
                JavaContainerBuilder.from("ignored"), ContainerizingMode.EXPLODED))
        .isNotNull();
  }

  @Test
  void testCreateContainerBuilder_noErrorIfWebInfDoesNotExist()
      throws IOException, InvalidImageReferenceException {
    setUpWarProject(temporaryFolder);
    setupGradleProjectPropertiesInstance();
    assertThat(
            gradleProjectProperties.createJibContainerBuilder(
                JavaContainerBuilder.from("ignored"), ContainerizingMode.EXPLODED))
        .isNotNull();
  }

  private void setupGradleProjectPropertiesInstance() {
    gradleProjectProperties =
        new GradleProjectProperties(
            project,
            mockLogger,
            mockTempDirectoryProvider,
            mockExtensionLoader,
            JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
  }

  @Test
  void testGetWarFilePath() throws IOException {
    File f = new File(temporaryFolder.toFile(), "output");
    f.mkdirs();

    Path outputDir = f.toPath();

    project.getPlugins().apply("war");
    setupGradleProjectPropertiesInstance();

    War war = project.getTasks().withType(War.class).getByName("war");
    war.getDestinationDirectory().set(outputDir.toFile());

    assertThat(gradleProjectProperties.getWarFilePath(project))
        .isEqualTo(outputDir.resolve("my-app.war").toString());
  }

  @Test
  void testGetWarFilePath_bootWar() throws IOException {
    File f = new File(temporaryFolder.toFile(), "output");
    f.mkdirs();

    Path outputDir = f.toPath();

    project.getPlugins().apply("war");
    project.getPlugins().apply("org.springframework.boot");

    War bootWar = project.getTasks().withType(War.class).getByName("bootWar");
    bootWar.getDestinationDirectory().set(outputDir.toFile());
    setupGradleProjectPropertiesInstance();

    assertThat(gradleProjectProperties.getWarFilePath(project))
        .isEqualTo(outputDir.resolve("my-app.war").toString());
  }

  @Test
  void testGetWarFilePath_bootWarDisabled() throws IOException {
    File f = new File(temporaryFolder.toFile(), "output");
    f.mkdirs();

    Path outputDir = f.toPath();

    project.getPlugins().apply("war");
    War war = project.getTasks().withType(War.class).getByName("war");
    war.getDestinationDirectory().set(outputDir.toFile());

    project.getPlugins().apply("org.springframework.boot");
    project.getTasks().getByName("bootWar").setEnabled(false);

    setupGradleProjectPropertiesInstance();
    assertThat(gradleProjectProperties.getWarFilePath(project))
        .isEqualTo(outputDir.resolve("my-app-plain.war").toString());
  }

  @Test
  void testGetSingleThreadedExecutor() {
    setupGradleProjectPropertiesInstance();
    assertNotNull(gradleProjectProperties.getSingleThreadedExecutor());
    gradleProjectProperties.singleThreadedExecutor = null; // pretend running from cache
    assertNotNull(gradleProjectProperties.getSingleThreadedExecutor());
  }

  @Test
  void testGetConsoleLogger() {
    setupGradleProjectPropertiesInstance();
    assertNotNull(gradleProjectProperties.getConsoleLogger());
  }

  @Test
  void testGetClassesOutputDirectory() {
    setupGradleProjectPropertiesInstance();
    assertNotNull(gradleProjectProperties.getClassesOutputDirectories());
    assertFalse(gradleProjectProperties.getClassesOutputDirectories().getFiles().isEmpty());
  }

  @Test
  void testGetResourcesOutputDirectory() {
    setupGradleProjectPropertiesInstance();
    assertNotNull(gradleProjectProperties.getResourcesOutputDirectory());
  }

  @Test
  void testGetClassFiles() throws IOException {
    setupGradleProjectPropertiesInstance();
    assertFalse(gradleProjectProperties.getClassFiles().isEmpty());
  }

  @Test
  void testGetDefaultCacheDir() throws IOException {
    setupGradleProjectPropertiesInstance();
    assertNotNull(gradleProjectProperties.getDefaultCacheDirectory());
  }

  @Test
  void testGetJarPluginName() throws IOException {
    setupGradleProjectPropertiesInstance();
    assertNotNull(gradleProjectProperties.getJarPluginName());
  }

  @Test
  void testGetTempProvider() {
    setupGradleProjectPropertiesInstance();
    assertNotNull(gradleProjectProperties.getTempDirectoryProvider());
  }

  @Test
  void testGetDependencies() throws URISyntaxException {
    setupGradleProjectPropertiesInstance();
    assertThat(gradleProjectProperties.getDependencies())
        .containsExactly(
            getResource("gradle/application/dependencies/library.jarC.jar"),
            getResource("gradle/application/dependencies/libraryB.jar"),
            getResource("gradle/application/dependencies/libraryA.jar"),
            getResource("gradle/application/dependencies/dependency-1.0.0.jar"),
            getResource("gradle/application/dependencies/more/dependency-1.0.0.jar"),
            getResource("gradle/application/dependencies/another/one/dependency-1.0.0.jar"),
            getResource("gradle/application/dependencies/dependencyX-1.0.0-SNAPSHOT.jar"))
        .inOrder();
  }

  private BuildContext setupBuildContext()
      throws InvalidImageReferenceException, CacheDirectoryCreationException {
    JavaContainerBuilder javaContainerBuilder =
        JavaContainerBuilder.from(RegistryImage.named("base"))
            .setAppRoot(AbsoluteUnixPath.get("/my/app"))
            .setModificationTimeProvider((ignored1, ignored2) -> EPOCH_PLUS_32);
    JibContainerBuilder jibContainerBuilder =
        gradleProjectProperties.createJibContainerBuilder(
            javaContainerBuilder, ContainerizingMode.EXPLODED);
    return JibContainerBuilderTestHelper.toBuildContext(
        jibContainerBuilder, Containerizer.to(RegistryImage.named("to")));
  }

  private Path setUpWarProject(Path webAppDirectory) throws IOException {
    File f = new File(temporaryFolder.toFile(), "output");
    f.mkdirs();

    File warOutputDir = f;
    zipUpDirectory(webAppDirectory, warOutputDir.toPath().resolve("my-app.war"));

    project.getPlugins().apply("war");
    War war = project.getTasks().withType(War.class).getByName("war");
    war.getDestinationDirectory().set(warOutputDir);

    // Make "GradleProjectProperties" use this folder to explode the WAR into.
    f = new File(temporaryFolder.toFile(), "exploded");
    f.mkdirs();

    Path unzipTarget = f.toPath();
    when(mockTempDirectoryProvider.newDirectory()).thenReturn(unzipTarget);
    return unzipTarget;
  }

  private static Path zipUpDirectory(Path sourceRoot, Path targetZip) throws IOException {
    try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(targetZip))) {
      for (Path source : new DirectoryWalker(sourceRoot).filterRoot().walk()) {

        StringJoiner pathJoiner = new StringJoiner("/", "", "");
        sourceRoot.relativize(source).forEach(element -> pathJoiner.add(element.toString()));
        String zipEntryPath =
            Files.isDirectory(source) ? pathJoiner.toString() + '/' : pathJoiner.toString();

        ZipEntry entry = new ZipEntry(zipEntryPath);
        zipOut.putNextEntry(entry);
        if (!Files.isDirectory(source)) {
          try (InputStream in = Files.newInputStream(source)) {
            ByteStreams.copy(in, zipOut);
          }
        }
        zipOut.closeEntry();
      }
    }
    return targetZip;
  }
}
