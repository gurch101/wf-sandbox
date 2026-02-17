package com.gurch.sandbox.forms;

import java.util.List;
import java.util.Optional;

/** Public API for form-file upload, download, search, and deletion. */
public interface FormFileApi {

  /** Persists metadata and bytes for a new uploaded file. */
  FormFileResponse upload(FormFileUploadRequest request);

  /** Finds one form file by identifier. */
  Optional<FormFileResponse> findById(Long id);

  /** Loads file metadata and content for download. */
  FormFileDownload download(Long id);

  /** Searches form files by optional filters and pagination. */
  List<FormFileResponse> search(FormFileSearchCriteria criteria);

  /** Deletes a form file and its stored content. */
  void deleteById(Long id);
}
