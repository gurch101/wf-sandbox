package com.gurch.sandbox.documenttemplates;

import com.gurch.sandbox.dto.PagedResponse;
import java.util.Optional;

/** Public API for document-template upload, download, search, and deletion. */
public interface DocumentTemplateApi {

  /** Persists metadata and bytes for a new uploaded file. */
  DocumentTemplateResponse upload(DocumentTemplateUploadRequest request);

  /** Updates metadata and optionally replaces template content. */
  DocumentTemplateResponse update(Long id, DocumentTemplateUpdateRequest request);

  /** Finds one document template by identifier. */
  Optional<DocumentTemplateResponse> findById(Long id);

  /** Loads file metadata and content for download. */
  DocumentTemplateDownload download(Long id);

  /** Generates one composed PDF from rendered template documents. */
  DocumentTemplateDownload generate(DocumentTemplateGenerateRequest request);

  /** Searches document templates by optional filters and pagination. */
  PagedResponse<DocumentTemplateResponse> search(DocumentTemplateSearchCriteria criteria);

  /** Deletes a document template and its stored content. */
  void deleteById(Long id);
}
