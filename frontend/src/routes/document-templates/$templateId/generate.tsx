import { Link, useParams } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import {
  useGenerateDocumentTemplate,
  useGetDocumentTemplateById,
} from "../../../api/generated";
import { DocumentTemplateFormFieldType } from "../../../api/model";
import type { DocumentTemplateFormField, GenerateInputFields } from "../../../api/model";
import {
  buttonClasses,
  cardClasses,
  describeError,
  downloadBlob,
  formatDateTime,
  inputClasses,
  normalizedFields,
  PageIntro,
  selectClasses,
  templateDisplayName,
  textareaClasses,
  toSlug,
} from "../shared";

type GenerateFormValues = Record<string, string | boolean>;
type TemplateParams = { templateId: number };

export function parseTemplateIdParams(params: Record<string, unknown>): TemplateParams {
  const raw = params.templateId;
  const value = typeof raw === "string" ? Number(raw) : raw;
  if (typeof value !== "number" || !Number.isFinite(value) || value <= 0) {
    throw new Error("Invalid template id");
  }
  return { templateId: Math.floor(value) };
}

export function stringifyTemplateIdParams(params: TemplateParams) {
  return { templateId: String(params.templateId) };
}

function buildInitialValues(fields: DocumentTemplateFormField[]): GenerateFormValues {
  const entries = fields.map((field) => {
    const key = field.key ?? "";
    const defaultValue =
      field.type === DocumentTemplateFormFieldType.CHECKBOX
        ? false
        : field.possibleValues?.[0] ?? "";
    return [key, defaultValue] as const;
  });
  return Object.fromEntries(entries);
}

function serializeFieldMap(values: GenerateFormValues): GenerateInputFields {
  const fields: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(values)) {
    if (typeof value === "string") {
      if (!value.trim()) {
        continue;
      }
      fields[key] = value;
      continue;
    }
    fields[key] = value;
  }
  return fields as GenerateInputFields;
}

function FieldInput({
  field,
  value,
  onChange,
}: {
  field: DocumentTemplateFormField;
  value: string | boolean;
  onChange: (nextValue: string | boolean) => void;
}) {
  const options = field.possibleValues ?? [];
  const key = field.key ?? "field";
  const type = field.type ?? DocumentTemplateFormFieldType.UNKNOWN;

  if (type === DocumentTemplateFormFieldType.CHECKBOX) {
    return (
      <label className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium text-slate-800">
        <input
          checked={Boolean(value)}
          className="h-4 w-4 rounded border-slate-300 text-teal-700 focus:ring-teal-700"
          type="checkbox"
          onChange={(event) => onChange(event.target.checked)}
        />
        <span>{key}</span>
      </label>
    );
  }

  if (type === DocumentTemplateFormFieldType.MULTILINE_TEXT) {
    return (
      <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
        <span>{key}</span>
        <textarea
          className={textareaClasses()}
          value={String(value)}
          onChange={(event) => onChange(event.target.value)}
        />
      </label>
    );
  }

  if (type === DocumentTemplateFormFieldType.SELECT) {
    return (
      <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
        <span>{key}</span>
        <select className={selectClasses()} value={String(value)} onChange={(event) => onChange(event.target.value)}>
          {options.length === 0 ? <option value="">Select an option</option> : null}
          {options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </label>
    );
  }

  if (type === DocumentTemplateFormFieldType.RADIO) {
    return (
      <fieldset className="grid gap-3 rounded-[1.5rem] border border-slate-200 bg-white p-4">
        <legend className="px-1 text-sm font-semibold text-slate-800">{key}</legend>
        {options.length === 0 ? (
          <input
            className={inputClasses()}
            value={String(value)}
            onChange={(event) => onChange(event.target.value)}
          />
        ) : (
          options.map((option) => (
            <label key={option} className="flex items-center gap-3 text-sm text-slate-700">
              <input
                checked={String(value) === option}
                type="radio"
                name={key}
                value={option}
                onChange={(event) => onChange(event.target.value)}
              />
              <span>{option}</span>
            </label>
          ))
        )}
      </fieldset>
    );
  }

  return (
    <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
      <span>{key}</span>
      <input className={inputClasses()} value={String(value)} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}

export function DocumentTemplateGeneratePage() {
  const { templateId } = useParams({ from: "/document-templates/$templateId/generate" });
  const [fieldValues, setFieldValues] = useState<GenerateFormValues>({});
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const templateQuery = useGetDocumentTemplateById(templateId);

  const generateMutation = useGenerateDocumentTemplate({
    mutation: {
      onSuccess: (blob) => {
        const name = templateDisplayName(templateQuery.data);
        downloadBlob(blob, `${toSlug(name)}-generated.pdf`);
      },
      onError: (error) => {
        setErrorMessage(describeError(error));
      },
    },
  });

  const fields = normalizedFields(templateQuery.data);

  useEffect(() => {
    if (fields.length === 0) {
      setFieldValues({});
      return;
    }
    setFieldValues(buildInitialValues(fields));
  }, [templateId, templateQuery.data?.updatedAt]);

  function setFieldValue(fieldKey: string, value: string | boolean) {
    setFieldValues((current) => ({ ...current, [fieldKey]: value }));
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    generateMutation.mutate({
      data: {
        documents: [
          {
            documentTemplateId: templateId,
            fields: serializeFieldMap(fieldValues),
          },
        ],
      },
    });
  }

  return (
    <section className="grid gap-5">
      <PageIntro
        eyebrow="Generate"
        title={templateQuery.data ? `Generate ${templateDisplayName(templateQuery.data)}` : "Generate Template"}
        description="This page is driven by the template’s parsed form map. Each input below comes directly from the backend’s discovered field metadata for this template."
      />

      {templateQuery.isLoading ? (
        <div className={cardClasses("bg-white/78 text-slate-600")}>Loading template details...</div>
      ) : null}

      {templateQuery.isError ? (
        <div className={cardClasses("bg-rose-50 text-rose-900")}>
          <p className="font-semibold">Could not load the selected template.</p>
          <pre className="mt-3 overflow-x-auto whitespace-pre-wrap text-sm">{describeError(templateQuery.error)}</pre>
        </div>
      ) : null}

      {templateQuery.data ? (
        <>
          <div className="grid gap-5 xl:grid-cols-[0.78fr_1.22fr]">
            <aside className={cardClasses("bg-white/82")}>
              <h2 className="mb-4 text-xl font-semibold text-slate-950">Template Details</h2>
              <dl className="grid gap-2 text-sm text-slate-600">
                <div className="flex justify-between gap-3">
                  <dt>ID</dt>
                  <dd className="font-medium text-slate-900">{templateQuery.data.id ?? "n/a"}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt>Language</dt>
                  <dd className="font-medium text-slate-900">{templateQuery.data.language ?? "n/a"}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt>Fields</dt>
                  <dd className="font-medium text-slate-900">{fields.length}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt>Updated</dt>
                  <dd className="font-medium text-slate-900">{formatDateTime(templateQuery.data.updatedAt)}</dd>
                </div>
              </dl>

              <div className="mt-5 rounded-[1.5rem] bg-slate-50 p-4">
                <p className="mb-2 text-sm font-semibold text-slate-900">Discovered fields</p>
                <ul className="grid gap-2 text-sm text-slate-600">
                  {fields.length === 0 ? <li>No form fields were discovered for this template.</li> : null}
                  {fields.map((field) => (
                    <li key={field.key} className="rounded-xl border border-slate-200 bg-white px-3 py-2">
                      <div className="font-medium text-slate-900">{field.key}</div>
                      <div>{field.type ?? "UNKNOWN"}</div>
                    </li>
                  ))}
                </ul>
              </div>
            </aside>

            <form className={cardClasses("grid gap-5 bg-white/82")} onSubmit={handleSubmit}>
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="mb-1 text-xs font-bold uppercase tracking-[0.22em] text-amber-800">Dynamic Form</p>
                  <h2 className="text-2xl font-semibold text-slate-950">Supply Template Values</h2>
                </div>
                <Link
                  className={buttonClasses("secondary")}
                  to="/document-templates"
                  search={{ page: 0, size: 12 }}
                >
                  Back to Search
                </Link>
              </div>

              {fields.length === 0 ? (
                <div className="rounded-[1.5rem] border border-dashed border-slate-300 bg-slate-50 p-6 text-sm leading-6 text-slate-600">
                  This template does not expose any parsed form fields. You can still generate it with an empty field map.
                </div>
              ) : (
                <div className="grid gap-4 md:grid-cols-2">
                  {fields.map((field) => {
                    const fieldKey = field.key ?? "";
                    return (
                      <FieldInput
                        key={fieldKey}
                        field={field}
                        value={fieldValues[fieldKey] ?? ""}
                        onChange={(value) => setFieldValue(fieldKey, value)}
                      />
                    );
                  })}
                </div>
              )}

              {errorMessage ? (
                <div className="rounded-[1.5rem] bg-rose-50 p-4 text-rose-900">
                  <p className="font-semibold">Generation failed.</p>
                  <pre className="mt-3 overflow-x-auto whitespace-pre-wrap text-sm">{errorMessage}</pre>
                </div>
              ) : null}

              <div className="flex flex-wrap gap-3">
                <button className={buttonClasses()} disabled={generateMutation.isPending} type="submit">
                  {generateMutation.isPending ? "Generating..." : "Generate PDF"}
                </button>
              </div>
            </form>
          </div>
        </>
      ) : null}
    </section>
  );
}
