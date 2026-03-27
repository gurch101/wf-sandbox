package com.gurch.sandbox.storage.internal;

import com.gurch.sandbox.storage.StorageProviderType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Local filesystem-backed storage service for binary content. */
@Service
public class LocalFilesystemStorageService implements StorageApi {

  private final Path root;

  public LocalFilesystemStorageService(@Value("${storage.local-root:./uploads}") String rootPath) {
    this.root = Path.of(rootPath).toAbsolutePath().normalize();
  }

  @Override
  public StorageWriteResult write(StorageWriteRequest request) throws IOException {
    validateWriteRequest(request);

    Files.createDirectories(root);

    String namespace = sanitizeNamespace(request.getNamespace());
    String sanitizedFilename = sanitizeFilename(request.getOriginalFilename());
    String storagePath = namespace + "/" + UUID.randomUUID() + "-" + sanitizedFilename;
    Path target = resolvePath(storagePath);

    Files.createDirectories(Objects.requireNonNull(target.getParent()));

    MessageDigest messageDigest = newSha256Digest();
    long contentSize;
    try (InputStream digestStream =
        new DigestInputStream(request.getContentStream(), messageDigest)) {
      contentSize = Files.copy(digestStream, target);
    }

    String checksum = HexFormat.of().formatHex(messageDigest.digest());
    return new StorageWriteResult(StorageProviderType.LOCAL_FS, storagePath, contentSize, checksum);
  }

  @Override
  public InputStream read(String storagePath) throws IOException {
    return Files.newInputStream(resolvePath(storagePath), StandardOpenOption.READ);
  }

  @Override
  public void delete(String storagePath) throws IOException {
    Path resolved = resolvePath(storagePath);
    Files.deleteIfExists(resolved);
  }

  private Path resolvePath(String storagePath) {
    Path resolved = root.resolve(storagePath).toAbsolutePath().normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Invalid storage path");
    }
    return resolved;
  }

  private static void validateWriteRequest(StorageWriteRequest request) {
    if (request == null || request.getContentStream() == null) {
      throw new IllegalArgumentException("content stream is required");
    }
    if (request.getOriginalFilename() == null || request.getOriginalFilename().isBlank()) {
      throw new IllegalArgumentException("original filename is required");
    }
  }

  private static String sanitizeNamespace(String namespace) {
    if (namespace == null || namespace.isBlank()) {
      return "default";
    }
    return namespace.trim().replaceAll("[^A-Za-z0-9._/-]", "_").replace("..", "_");
  }

  private static String sanitizeFilename(String originalFilename) {
    Path filenamePath = Path.of(originalFilename).getFileName();
    String filename =
        filenamePath != null ? filenamePath.toString().trim() : originalFilename.trim();
    if (filename.isBlank()) {
      return "upload.bin";
    }
    return filename.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private static MessageDigest newSha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 digest is unavailable", e);
    }
  }
}
