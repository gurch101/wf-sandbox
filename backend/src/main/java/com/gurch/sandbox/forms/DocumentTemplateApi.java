package com.gurch.sandbox.forms;

import java.util.List;
import java.util.Optional;

/** Public API for document-template upload, download, search, and deletion. */
public interface DocumentTemplateApi {

  /** Persists metadata and bytes for a new uploaded file. */
  DocumentTemplateResponse upload(DocumentTemplateUploadRequest request);

  /** Finds one document template by identifier. */
  Optional<DocumentTemplateResponse> findById(Long id);

  /** Loads file metadata and content for download. */
  DocumentTemplateDownload download(Long id);

  /** Searches document templates by optional filters and pagination. */
  List<DocumentTemplateResponse> search(DocumentTemplateSearchCriteria criteria);

  /** Deletes a document template and its stored content. */
  void deleteById(Long id);
}
