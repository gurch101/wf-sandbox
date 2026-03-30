import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import {
  downloadDocumentTemplate,
  useSearchDocumentTemplates,
} from "../../api/generated";
import {
  buttonClasses,
  cardClasses,
  describeError,
  downloadBlob,
  fieldCount,
  formatDateTime,
  inputClasses,
  PageIntro,
  templateDisplayName,
  templateLanguageLabel,
  toSlug,
} from "./shared";

type DocumentTemplateSearch = {
  nameBegins?: string;
  page: number;
  size: number;
};

function parsePositiveInteger(value: unknown, fallback: number) {
  if (typeof value === "number" && Number.isFinite(value) && value >= 0) {
    return Math.floor(value);
  }
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value);
    if (Number.isFinite(parsed) && parsed >= 0) {
      return Math.floor(parsed);
    }
  }
  return fallback;
}

export function validateDocumentTemplateSearch(search: Record<string, unknown>): DocumentTemplateSearch {
  const rawSize = parsePositiveInteger(search.size, 12);
  return {
    nameBegins: typeof search.nameBegins === "string" && search.nameBegins.trim() ? search.nameBegins : undefined,
    page: parsePositiveInteger(search.page, 0),
    size: rawSize === 0 ? 12 : Math.min(rawSize, 100),
  };
}

export function DocumentTemplateSearchPage() {
  const navigate = useNavigate({ from: "/document-templates" });
  const search = useSearch({ from: "/document-templates" });
  const [draftName, setDraftName] = useState(search.nameBegins ?? "");
  const [downloadingId, setDownloadingId] = useState<number | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  useEffect(() => {
    setDraftName(search.nameBegins ?? "");
  }, [search.nameBegins]);

  const templatesQuery = useSearchDocumentTemplates({
    nameBegins: search.nameBegins?.trim() || undefined,
    page: search.page,
    size: search.size,
  });

  async function handleSearchSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await navigate({
      to: "/document-templates",
      search: {
        nameBegins: draftName.trim() || undefined,
        page: 0,
        size: search.size,
      },
    });
  }

  async function handlePageChange(nextPage: number) {
    await navigate({
      to: "/document-templates",
      search: {
        nameBegins: search.nameBegins,
        page: nextPage,
        size: search.size,
      },
    });
  }

  async function handleDownload(id: number, filename: string) {
    setDownloadError(null);
    setDownloadingId(id);
    try {
      const blob = await downloadDocumentTemplate(id);
      downloadBlob(blob, `${toSlug(filename)}.bin`);
    } catch (error) {
      setDownloadError(describeError(error));
    } finally {
      setDownloadingId(null);
    }
  }

  const items = templatesQuery.data?.items ?? [];
  const currentPage = templatesQuery.data?.page ?? search.page;
  const totalPages = templatesQuery.data?.totalPages ?? 0;
  const totalElements = templatesQuery.data?.totalElements ?? 0;

  return (
    <section className="grid gap-5">
      <PageIntro
        eyebrow="Search"
        title="Template Catalog"
        description="Filter uploaded templates, inspect their parsed field counts, and jump directly into a generate screen tailored to that template’s form map."
      />

      <div className={cardClasses("bg-white/78")}>
        <form className="grid gap-4 md:grid-cols-[1fr_auto]" onSubmit={handleSearchSubmit}>
          <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
            <span>Name begins with</span>
            <input
              className={inputClasses()}
              placeholder="Client Intake"
              value={draftName}
              onChange={(event) => setDraftName(event.target.value)}
            />
          </label>
          <div className="flex items-end gap-3">
            <button className={buttonClasses()} type="submit">
              Search
            </button>
            <Link className={buttonClasses("secondary")} to="/document-templates/new">
              Upload New Template
            </Link>
          </div>
        </form>
      </div>

      {templatesQuery.isLoading ? (
        <div className={cardClasses("bg-white/78 text-slate-600")}>Loading templates...</div>
      ) : null}

      {templatesQuery.isError ? (
        <div className={cardClasses("bg-rose-50 text-rose-900")}>
          <p className="font-semibold">Could not load templates.</p>
          <pre className="mt-3 overflow-x-auto whitespace-pre-wrap text-sm">{describeError(templatesQuery.error)}</pre>
        </div>
      ) : null}

      {downloadError ? (
        <div className={cardClasses("bg-rose-50 text-rose-900")}>
          <p className="font-semibold">Download failed.</p>
          <pre className="mt-3 overflow-x-auto whitespace-pre-wrap text-sm">{downloadError}</pre>
        </div>
      ) : null}

      {!templatesQuery.isLoading && !templatesQuery.isError ? (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {items.length === 0 ? (
              <div className={cardClasses("bg-white/78 text-slate-600 md:col-span-2 xl:col-span-3")}>
                No templates matched this filter.
              </div>
            ) : null}

            {items.map((template) => {
              const templateId = template.id;
              const name = templateDisplayName(template);
              return (
                <article key={templateId ?? name} className={cardClasses("bg-white/82")}>
                  <div className="mb-4 flex items-start justify-between gap-3">
                    <div>
                      <p className="mb-1 text-xs font-bold uppercase tracking-[0.22em] text-amber-800">
                        {templateLanguageLabel(template.language)}
                      </p>
                      <h2 className="text-2xl font-semibold tracking-[-0.03em] text-slate-950">
                        {name}
                      </h2>
                    </div>
                    <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-700">
                      {fieldCount(template)} fields
                    </span>
                  </div>

                  <dl className="grid gap-2 text-sm text-slate-600">
                    <div className="flex justify-between gap-3">
                      <dt>Template ID</dt>
                      <dd className="font-medium text-slate-900">{templateId ?? "n/a"}</dd>
                    </div>
                    <div className="flex justify-between gap-3">
                      <dt>Tenant</dt>
                      <dd className="font-medium text-slate-900">{template.tenantId ?? "Global"}</dd>
                    </div>
                    <div className="flex justify-between gap-3">
                      <dt>Updated</dt>
                      <dd className="font-medium text-slate-900">{formatDateTime(template.updatedAt)}</dd>
                    </div>
                  </dl>

                  <p className="mt-4 line-clamp-3 min-h-16 text-sm leading-6 text-slate-600">
                    {template.enDescription?.trim() || template.frDescription?.trim() || "No description provided."}
                  </p>

                  <div className="mt-5 flex flex-wrap gap-3">
                    {templateId ? (
                      <Link
                        className={buttonClasses()}
                        to="/document-templates/$templateId/generate"
                        params={{ templateId }}
                      >
                        Generate
                      </Link>
                    ) : null}
                    {templateId ? (
                      <button
                        className={buttonClasses("secondary")}
                        type="button"
                        onClick={() => void handleDownload(templateId, name)}
                      >
                        {downloadingId === templateId ? "Downloading..." : "Download Template"}
                      </button>
                    ) : null}
                  </div>
                </article>
              );
            })}
          </div>

          {items.length > 0 ? (
            <div className={cardClasses("bg-white/78")}>
              <div className="flex flex-wrap items-center justify-between gap-4">
                <p className="text-sm text-slate-600">
                  Showing page {currentPage + 1} of {Math.max(totalPages, 1)}. {totalElements} total templates.
                </p>
                <div className="flex gap-3">
                  <button
                    className={buttonClasses("secondary")}
                    type="button"
                    disabled={currentPage <= 0}
                    onClick={() => void handlePageChange(Math.max(currentPage - 1, 0))}
                  >
                    Previous
                  </button>
                  <button
                    className={buttonClasses("secondary")}
                    type="button"
                    disabled={totalPages === 0 || currentPage >= totalPages - 1}
                    onClick={() => void handlePageChange(currentPage + 1)}
                  >
                    Next
                  </button>
                </div>
              </div>
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  );
}
