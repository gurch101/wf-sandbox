import type {
  DocumentTemplateFormField,
  DocumentTemplateResponse,
  DocumentTemplateResponseLanguage,
} from "../../api/model";

export function cardClasses(extra = "") {
  return `rounded-[2rem] border border-black/8 p-5 shadow-[0_14px_40px_rgba(44,55,54,0.08)] backdrop-blur-md ${extra}`.trim();
}

export function inputClasses() {
  return "w-full rounded-2xl border border-slate-300/80 bg-white/95 px-4 py-3 text-slate-900 shadow-sm outline-none transition focus:border-teal-700 focus:ring-4 focus:ring-teal-700/10";
}

export function textareaClasses() {
  return `${inputClasses()} min-h-32 resize-y`;
}

export function selectClasses() {
  return `${inputClasses()} appearance-none`;
}

export function buttonClasses(tone: "primary" | "secondary" = "primary") {
  if (tone === "secondary") {
    return "rounded-full border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:border-slate-400";
  }
  return "rounded-full bg-slate-950 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800";
}

export function describeError(error: unknown): string {
  if (typeof error === "object" && error !== null && "payload" in error) {
    const payload = (error as { payload?: unknown }).payload;
    if (payload != null) {
      return typeof payload === "string" ? payload : JSON.stringify(payload, null, 2);
    }
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Unexpected error";
}

export function formatDateTime(value?: string): string {
  if (!value) {
    return "n/a";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(parsed);
}

export function templateDisplayName(template?: DocumentTemplateResponse): string {
  return template?.enName?.trim() || template?.frName?.trim() || `Template #${template?.id ?? "?"}`;
}

export function templateLanguageLabel(language?: DocumentTemplateResponseLanguage): string {
  return language === "FRENCH" ? "French" : language === "ENGLISH" ? "English" : "Unknown";
}

export function fieldCount(template?: DocumentTemplateResponse): number {
  return template?.formMap?.fields?.length ?? 0;
}

export function normalizedFields(template?: DocumentTemplateResponse): DocumentTemplateFormField[] {
  return template?.formMap?.fields?.filter((field): field is DocumentTemplateFormField => !!field.key) ?? [];
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export function toSlug(value: string): string {
  const slug = value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return slug || "document-template";
}

export function PageIntro({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description: string;
}) {
  return (
    <div className={cardClasses("bg-white/68")}>
      <p className="mb-2 text-xs font-bold uppercase tracking-[0.22em] text-amber-800">{eyebrow}</p>
      <h1 className="mb-3 max-w-3xl text-4xl font-semibold tracking-[-0.05em] text-slate-950 sm:text-6xl">
        {title}
      </h1>
      <p className="max-w-3xl text-base leading-7 text-slate-600 sm:text-lg">{description}</p>
    </div>
  );
}
