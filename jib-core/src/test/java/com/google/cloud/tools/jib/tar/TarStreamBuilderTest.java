/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.tar;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.blob.Blobs;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link TarStreamBuilder}. */
class TarStreamBuilderTest {

  private Path fileA;
  private Path fileB;
  private Path directoryA;
  private byte[] fileAContents;
  private byte[] fileBContents;
  private final TarStreamBuilder testTarStreamBuilder = new TarStreamBuilder();

  @BeforeEach
  void setup() throws URISyntaxException, IOException {
    // Gets the test resource files.
    fileA = Paths.get(Resources.getResource("core/fileA").toURI());
    fileB = Paths.get(Resources.getResource("core/fileB").toURI());
    directoryA = Paths.get(Resources.getResource("core/directoryA").toURI());

    fileAContents = Files.readAllBytes(fileA);
    fileBContents = Files.readAllBytes(fileB);
  }

  @Test
  void testToBlob_tarArchiveEntries() throws IOException {
    setUpWithTarEntries();
    verifyBlobWithoutCompression();
  }

  @Test
  void testToBlob_strings() throws IOException {
    setUpWithStrings();
    verifyBlobWithoutCompression();
  }

  @Test
  void testToBlob_stringsAndTarArchiveEntries() throws IOException {
    setUpWithStringsAndTarEntries();
    verifyBlobWithoutCompression();
  }

  @Test
  void testToBlob_tarArchiveEntriesWithCompression() throws IOException {
    setUpWithTarEntries();
    verifyBlobWithCompression();
  }

  @Test
  void testToBlob_stringsWithCompression() throws IOException {
    setUpWithStrings();
    verifyBlobWithCompression();
  }

  @Test
  void testToBlob_stringsAndTarArchiveEntriesWithCompression() throws IOException {
    setUpWithStringsAndTarEntries();
    verifyBlobWithCompression();
  }

  @Test
  void testToBlob_multiByte() throws IOException {
    testTarStreamBuilder.addByteEntry(
        "日本語".getBytes(StandardCharsets.UTF_8), "test", Instant.EPOCH);
    testTarStreamBuilder.addByteEntry(
        "asdf".getBytes(StandardCharsets.UTF_8), "crepecake", Instant.EPOCH);
    testTarStreamBuilder.addBlobEntry(
        Blobs.from("jib"), "jib".getBytes(StandardCharsets.UTF_8).length, "jib", Instant.EPOCH);

    // Writes the BLOB and captures the output.
    ByteArrayOutputStream tarByteOutputStream = new ByteArrayOutputStream();
    OutputStream compressorStream = new GZIPOutputStream(tarByteOutputStream);
    testTarStreamBuilder.writeAsTarArchiveTo(compressorStream);

    // Rearrange the output into input for verification.
    ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(tarByteOutputStream.toByteArray());
    InputStream tarByteInputStream = new GZIPInputStream(byteArrayInputStream);
    TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(tarByteInputStream);

    // Verify multi-byte characters are written/read correctly
    TarArchiveEntry headerFile = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("test", headerFile.getName());
    Assert.assertEquals(
        "日本語", new String(ByteStreams.toByteArray(tarArchiveInputStream), StandardCharsets.UTF_8));

    headerFile = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("crepecake", headerFile.getName());
    Assert.assertEquals(
        "asdf", new String(ByteStreams.toByteArray(tarArchiveInputStream), StandardCharsets.UTF_8));

    headerFile = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("jib", headerFile.getName());
    Assert.assertEquals(
        "jib", new String(ByteStreams.toByteArray(tarArchiveInputStream), StandardCharsets.UTF_8));

    Assert.assertNull(tarArchiveInputStream.getNextTarEntry());
  }

  @Test
  void testToBlob_modificationTime() throws IOException {
    testTarStreamBuilder.addByteEntry(
        "foo".getBytes(StandardCharsets.UTF_8), "foo", Instant.ofEpochSecond(1234));
    testTarStreamBuilder.addBlobEntry(
        Blobs.from("bar"),
        "bar".getBytes(StandardCharsets.UTF_8).length,
        "bar",
        Instant.ofEpochSecond(3));

    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    testTarStreamBuilder.writeAsTarArchiveTo(outStream);

    TarArchiveInputStream tarInStream =
        new TarArchiveInputStream(new ByteArrayInputStream(outStream.toByteArray()));
    TarArchiveEntry entry1 = tarInStream.getNextTarEntry();
    TarArchiveEntry entry2 = tarInStream.getNextTarEntry();

    assertThat(entry1.getName()).isEqualTo("foo");
    assertThat(entry1.getModTime().toInstant()).isEqualTo(Instant.ofEpochSecond(1234));
    assertThat(entry2.getName()).isEqualTo("bar");
    assertThat(entry2.getModTime().toInstant()).isEqualTo(Instant.ofEpochSecond(3));
  }

  /** Creates a TarStreamBuilder using TarArchiveEntries. */
  private void setUpWithTarEntries() throws IOException {
    // Prepares a test TarStreamBuilder.
    testTarStreamBuilder.addTarArchiveEntry(
        new TarArchiveEntry(fileA, "some/path/to/resourceFileA"));
    testTarStreamBuilder.addTarArchiveEntry(new TarArchiveEntry(fileB, "crepecake"));
    testTarStreamBuilder.addTarArchiveEntry(new TarArchiveEntry(directoryA, "some/path/to"));
    testTarStreamBuilder.addTarArchiveEntry(
        new TarArchiveEntry(
            fileA,
            "some/really/long/path/that/exceeds/100/characters/abcdefghijklmnopqrstuvwxyz0123456789012345678901234567890"));
  }

  /** Creates a TarStreamBuilder using Strings. */
  private void setUpWithStrings() throws IOException {
    // Prepares a test TarStreamBuilder.
    testTarStreamBuilder.addByteEntry(fileAContents, "some/path/to/resourceFileA", Instant.EPOCH);
    testTarStreamBuilder.addByteEntry(fileBContents, "crepecake", Instant.EPOCH);
    testTarStreamBuilder.addTarArchiveEntry(new TarArchiveEntry(directoryA, "some/path/to"));
    testTarStreamBuilder.addByteEntry(
        fileAContents,
        "some/really/long/path/that/exceeds/100/characters/abcdefghijklmnopqrstuvwxyz0123456789012345678901234567890",
        Instant.EPOCH);
  }

  /** Creates a TarStreamBuilder using Strings and TarArchiveEntries. */
  private void setUpWithStringsAndTarEntries() throws IOException {
    // Prepares a test TarStreamBuilder.
    testTarStreamBuilder.addByteEntry(fileAContents, "some/path/to/resourceFileA", Instant.EPOCH);
    testTarStreamBuilder.addTarArchiveEntry(new TarArchiveEntry(fileB, "crepecake"));
    testTarStreamBuilder.addTarArchiveEntry(new TarArchiveEntry(directoryA, "some/path/to"));
    testTarStreamBuilder.addByteEntry(
        fileAContents,
        "some/really/long/path/that/exceeds/100/characters/abcdefghijklmnopqrstuvwxyz0123456789012345678901234567890",
        Instant.EPOCH);
  }

  /** Creates a compressed blob from the TarStreamBuilder and verifies it. */
  private void verifyBlobWithCompression() throws IOException {
    // Writes the BLOB and captures the output.
    ByteArrayOutputStream tarByteOutputStream = new ByteArrayOutputStream();
    OutputStream compressorStream = new GZIPOutputStream(tarByteOutputStream);
    testTarStreamBuilder.writeAsTarArchiveTo(compressorStream);

    // Rearrange the output into input for verification.
    ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(tarByteOutputStream.toByteArray());
    InputStream tarByteInputStream = new GZIPInputStream(byteArrayInputStream);
    TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(tarByteInputStream);
    verifyTarArchive(tarArchiveInputStream);
  }

  /** Creates an uncompressed blob from the TarStreamBuilder and verifies it. */
  private void verifyBlobWithoutCompression() throws IOException {
    // Writes the BLOB and captures the output.
    ByteArrayOutputStream tarByteOutputStream = new ByteArrayOutputStream();
    testTarStreamBuilder.writeAsTarArchiveTo(tarByteOutputStream);

    // Rearrange the output into input for verification.
    ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(tarByteOutputStream.toByteArray());
    TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(byteArrayInputStream);
    verifyTarArchive(tarArchiveInputStream);
  }

  /**
   * Helper method to verify that the files were archived correctly by reading {@code
   * tarArchiveInputStream}.
   */
  private void verifyTarArchive(TarArchiveInputStream tarArchiveInputStream) throws IOException {
    // Verifies fileA was archived correctly.
    TarArchiveEntry headerFileA = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("some/path/to/resourceFileA", headerFileA.getName());
    byte[] fileAString = ByteStreams.toByteArray(tarArchiveInputStream);
    Assert.assertArrayEquals(fileAContents, fileAString);

    // Verifies fileB was archived correctly.
    TarArchiveEntry headerFileB = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("crepecake", headerFileB.getName());
    byte[] fileBString = ByteStreams.toByteArray(tarArchiveInputStream);
    Assert.assertArrayEquals(fileBContents, fileBString);

    // Verifies directoryA was archived correctly.
    TarArchiveEntry headerDirectoryA = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("some/path/to/", headerDirectoryA.getName());

    // Verifies the long file was archived correctly.
    TarArchiveEntry headerFileALong = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals(
        "some/really/long/path/that/exceeds/100/characters/abcdefghijklmnopqrstuvwxyz0123456789012345678901234567890",
        headerFileALong.getName());
    byte[] fileALongString = ByteStreams.toByteArray(tarArchiveInputStream);
    Assert.assertArrayEquals(fileAContents, fileALongString);

    Assert.assertNull(tarArchiveInputStream.getNextTarEntry());
  }
}
