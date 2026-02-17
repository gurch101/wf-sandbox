package com.gurch.sandbox.forms.internal;

import java.io.IOException;

/** Abstraction for persisted file content storage. */
public interface FormStorageProvider {

  FormStorageWriteResult write(String originalFilename, byte[] content) throws IOException;

  byte[] read(String storagePath) throws IOException;

  void delete(String storagePath) throws IOException;
}
