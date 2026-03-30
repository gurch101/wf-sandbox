import { Link, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { useUploadDocumentTemplate } from "../../api/generated";
import { DocumentTemplateUploadRequestLanguage } from "../../api/model";
import {
  buttonClasses,
  cardClasses,
  describeError,
  inputClasses,
  PageIntro,
  selectClasses,
  textareaClasses,
} from "./shared";

type UploadFormState = {
  enName: string;
  frName: string;
  enDescription: string;
  frDescription: string;
  language: keyof typeof DocumentTemplateUploadRequestLanguage;
  tenantId: string;
  file: File | null;
};

const initialFormState: UploadFormState = {
  enName: "",
  frName: "",
  enDescription: "",
  frDescription: "",
  language: "ENGLISH",
  tenantId: "",
  file: null,
};

export function DocumentTemplateCreatePage() {
  const navigate = useNavigate();
  const [form, setForm] = useState<UploadFormState>(initialFormState);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const uploadMutation = useUploadDocumentTemplate({
    mutation: {
      onSuccess: async (response) => {
        const templateId = response.id;
        if (!templateId) {
          setErrorMessage("Upload succeeded but the API did not return a template id.");
          return;
        }
        await navigate({
          to: "/document-templates/$templateId/generate",
          params: { templateId },
        });
      },
      onError: (error) => {
        setErrorMessage(describeError(error));
      },
    },
  });

  const canSubmit = !!form.enName.trim() && !!form.file;

  function updateField<K extends keyof UploadFormState>(key: K, value: UploadFormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSubmit || !form.file) {
      return;
    }

    setErrorMessage(null);
    uploadMutation.mutate({
      data: {
        file: form.file,
        request: {
          enName: form.enName.trim(),
          frName: form.frName.trim() || undefined,
          enDescription: form.enDescription.trim() || undefined,
          frDescription: form.frDescription.trim() || undefined,
          language: DocumentTemplateUploadRequestLanguage[form.language],
          tenantId: form.tenantId.trim() ? Number(form.tenantId.trim()) : undefined,
        },
      },
    });
  }

  return (
    <section className="grid gap-5">
      <PageIntro
        eyebrow="Create"
        title="Upload a New Template"
        description="Submit a DOCX or PDF template, let the backend introspect its merge fields, and then jump straight to a generation screen for validation."
      />

      <form className={cardClasses("grid gap-5 bg-white/80")} onSubmit={handleSubmit}>
        <div className="grid gap-5 md:grid-cols-2">
          <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
            <span>English name</span>
            <input
              className={inputClasses()}
              placeholder="Client Intake Form"
              value={form.enName}
              onChange={(event) => updateField("enName", event.target.value)}
            />
          </label>

          <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
            <span>French name</span>
            <input
              className={inputClasses()}
              placeholder="Formulaire d'accueil"
              value={form.frName}
              onChange={(event) => updateField("frName", event.target.value)}
            />
          </label>

          <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
            <span>Language</span>
            <select
              className={selectClasses()}
              value={form.language}
              onChange={(event) =>
                updateField("language", event.target.value as keyof typeof DocumentTemplateUploadRequestLanguage)
              }
            >
              <option value="ENGLISH">English</option>
              <option value="FRENCH">French</option>
            </select>
          </label>

          <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
            <span>Tenant ID</span>
            <input
              className={inputClasses()}
              inputMode="numeric"
              placeholder="Leave empty for global"
              value={form.tenantId}
              onChange={(event) => updateField("tenantId", event.target.value)}
            />
          </label>
        </div>

        <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
          <span>English description</span>
          <textarea
            className={textareaClasses()}
            placeholder="Describe when this template should be used."
            value={form.enDescription}
            onChange={(event) => updateField("enDescription", event.target.value)}
          />
        </label>

        <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
          <span>French description</span>
          <textarea
            className={textareaClasses()}
            placeholder="Description française optionnelle."
            value={form.frDescription}
            onChange={(event) => updateField("frDescription", event.target.value)}
          />
        </label>

        <label className="grid gap-2 text-sm font-semibold text-slate-800">
          <span>Template file</span>
          <input
            className="block w-full rounded-2xl border border-dashed border-slate-300 bg-white px-4 py-5 text-sm text-slate-700 file:mr-4 file:rounded-full file:border-0 file:bg-slate-950 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-white"
            type="file"
            accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            onChange={(event) => updateField("file", event.target.files?.[0] ?? null)}
          />
          <span className="text-sm font-normal text-slate-500">
            Upload the exact template file the backend should introspect.
          </span>
        </label>

        {errorMessage ? (
          <div className="rounded-[1.5rem] bg-rose-50 p-4 text-rose-900">
            <p className="font-semibold">Upload failed.</p>
            <pre className="mt-3 overflow-x-auto whitespace-pre-wrap text-sm">{errorMessage}</pre>
          </div>
        ) : null}

        <div className="flex flex-wrap gap-3">
          <button className={buttonClasses()} disabled={!canSubmit || uploadMutation.isPending} type="submit">
            {uploadMutation.isPending ? "Uploading..." : "Upload Template"}
          </button>
          <Link
            className={buttonClasses("secondary")}
            to="/document-templates"
            search={{ page: 0, size: 12 }}
          >
            Back to Search
          </Link>
        </div>
      </form>
    </section>
  );
}
