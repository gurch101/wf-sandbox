package com.gurch.sandbox.forms.internal;

import com.gurch.sandbox.forms.FormStorageProviderType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local filesystem-backed storage provider for uploaded form files. */
@Component
final class LocalFilesystemFormStorageProvider implements FormStorageProvider {

  private final Path root;

  LocalFilesystemFormStorageProvider(
      @Value("${forms.storage.local-root:./uploads/forms}") String rootPath) {
    this.root = Path.of(rootPath).toAbsolutePath().normalize();
  }

  @Override
  public FormStorageWriteResult write(String originalFilename, byte[] content) throws IOException {
    Files.createDirectories(root);
    String sanitized = sanitizeFilename(originalFilename);
    String storagePath = UUID.randomUUID() + "-" + sanitized;
    Path target = resolvePath(storagePath);
    Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    return new FormStorageWriteResult(FormStorageProviderType.LOCAL_FS, storagePath);
  }

  @Override
  public byte[] read(String storagePath) throws IOException {
    return Files.readAllBytes(resolvePath(storagePath));
  }

  @Override
  public void delete(String storagePath) throws IOException {
    Files.deleteIfExists(resolvePath(storagePath));
  }

  private Path resolvePath(String storagePath) {
    Path resolved = root.resolve(storagePath).toAbsolutePath().normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Invalid storage path");
    }
    return resolved;
  }

  private static String sanitizeFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return "upload.bin";
    }
    Path filenamePath = Path.of(originalFilename).getFileName();
    String filename =
        filenamePath != null ? filenamePath.toString().trim() : originalFilename.trim();
    if (filename.isBlank()) {
      return "upload.bin";
    }
    return filename.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
