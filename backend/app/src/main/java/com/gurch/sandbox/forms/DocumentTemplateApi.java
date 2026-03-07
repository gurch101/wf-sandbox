package com.gurch.sandbox.forms;

import com.gurch.sandbox.dto.PagedResponse;
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
  PagedResponse<DocumentTemplateResponse> search(DocumentTemplateSearchCriteria criteria);

  /** Deletes a document template and its stored content. */
  void deleteById(Long id);
}
