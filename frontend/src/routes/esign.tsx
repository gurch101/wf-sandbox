import { useQueryClient } from "@tanstack/react-query";
import { useNavigate, useSearch } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import {
  useCreateEsignEmbeddedView,
  useCreateEsignEnvelope,
  useGetEsignEnvelopeById,
} from "../api/generated";
import type {
  EsignEnvelopeResponse,
  EsignSignerResponse,
} from "../api/model";
import {
  buttonClasses,
  cardClasses,
  describeError,
  formatDateTime,
  inputClasses,
  PageIntro,
  textareaClasses,
} from "./document-templates/shared";

type EsignSearch = {
  envelopeId?: number;
  roleKey?: string;
  signingUrl?: string;
};

type SigningReturnMessage = {
  type: "docusign-return";
  event: string | null;
};

type FormState = {
  subject: string;
  message: string;
  signerName: string;
  roleKey: string;
  file: File | null;
};

const initialFormState: FormState = {
  subject: "Please sign in person",
  message: "",
  signerName: "",
  roleKey: "s1",
  file: null,
};

function parseEnvelopeId(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return undefined;
}

export function validateEsignSearch(search: Record<string, unknown>): EsignSearch {
  return {
    envelopeId: parseEnvelopeId(search.envelopeId),
    roleKey: typeof search.roleKey === "string" ? search.roleKey : undefined,
    signingUrl: typeof search.signingUrl === "string" ? search.signingUrl : undefined,
  };
}

function readFirstSigner(envelope: EsignEnvelopeResponse | null): EsignSignerResponse | null {
  return envelope?.signers?.[0] ?? null;
}

function resolveEmbeddedSigningLocale(): string | undefined {
  const appLanguage = document.documentElement.lang.trim();
  if (appLanguage) {
    return appLanguage.replace(/-/g, "_");
  }
  const browserLanguage = window.navigator.language?.trim();
  return browserLanguage ? browserLanguage.replace(/-/g, "_") : undefined;
}

function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm">
      <dt className="text-slate-600">{label}</dt>
      <dd className="text-right font-medium text-slate-900">{value}</dd>
    </div>
  );
}

export function EsignPage() {
  const navigate = useNavigate({ from: "/esign" });
  const queryClient = useQueryClient();
  const search = useSearch({ from: "/esign" });
  const [form, setForm] = useState<FormState>(initialFormState);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [signingEvent, setSigningEvent] = useState<string | null>(null);

  const envelopeQuery = useGetEsignEnvelopeById(search.envelopeId ?? 0, {
    query: {
      enabled: !!search.envelopeId,
    },
  });

  const createEmbeddedViewMutation = useCreateEsignEmbeddedView({
    mutation: {
      onSuccess: async (embedded, variables) => {
        await navigate({
          to: "/esign",
          search: {
            envelopeId: search.envelopeId,
            roleKey: variables.roleKey,
            signingUrl: embedded.signingUrl ?? undefined,
          },
        });
      },
      onError: (error) => {
        setErrorMessage(describeError(error));
      },
    },
  });

  const createEnvelopeMutation = useCreateEsignEnvelope({
    mutation: {
      onSuccess: async (envelope, variables) => {
        queryClient.setQueryData(envelopeQuery.queryKey, envelope);
        const roleKey = variables.data.request.signers?.[0]?.anchorKey ?? form.roleKey.trim();

        await navigate({
          to: "/esign",
          search: {
            envelopeId: envelope.id,
            roleKey,
            signingUrl: undefined,
          },
        });

        if (!envelope.id) {
          return;
        }

        await createEmbeddedViewMutation.mutateAsync({
          id: envelope.id,
          roleKey,
          locale: resolveEmbeddedSigningLocale(),
          headers: {
            // One key per user action. Retries of this mutation reuse the same key.
            "Idempotency-Key": crypto.randomUUID(),
          },
        });
      },
      onError: (error) => {
        setErrorMessage(describeError(error));
      },
    },
  });

  const firstSigner = useMemo(
    () => readFirstSigner(envelopeQuery.data ?? null),
    [envelopeQuery.data],
  );

  useEffect(() => {
    function handleMessage(event: MessageEvent<SigningReturnMessage>) {
      if (event.origin !== window.location.origin) {
        return;
      }
      if (event.data?.type !== "docusign-return") {
        return;
      }

      setSigningEvent(event.data.event ?? "unknown");
      void navigate({
        to: "/esign",
        search: {
          envelopeId: search.envelopeId,
          roleKey: search.roleKey,
          signingUrl: undefined,
        },
      });
      void envelopeQuery.refetch();
    }

    window.addEventListener("message", handleMessage);
    return () => window.removeEventListener("message", handleMessage);
  }, [envelopeQuery, navigate, search.envelopeId, search.roleKey]);

  const canSubmit =
    !!form.file &&
    !!form.subject.trim() &&
    !!form.signerName.trim() &&
    !!form.roleKey.trim();

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSubmit || !form.file) {
      return;
    }

    setErrorMessage(null);
    setSigningEvent(null);
    createEnvelopeMutation.mutate({
      data: {
        file: form.file,
        request: {
          subject: form.subject.trim(),
          message: form.message.trim() || undefined,
          deliveryMode: "IN_PERSON",
          remindersEnabled: false,
          signers: [
            {
              anchorKey: form.roleKey.trim(),
              fullName: form.signerName.trim(),
              email: undefined,
              authMethod: "NONE",
              passcode: undefined,
              smsNumber: undefined,
              routingOrder: 1,
            },
          ],
        },
      },
    });
  }

  return (
    <section className="grid gap-5">
      <PageIntro
        eyebrow="E-Sign"
        title="In-Person Signing"
        description="Upload a PDF, create an in-person envelope, and complete the embedded signing ceremony directly inside the app."
      />

      <div className="grid gap-5 xl:grid-cols-[0.95fr_1.05fr]">
        <form className={cardClasses("grid gap-5 bg-white/78")} onSubmit={handleSubmit}>
          <div>
            <p className="mb-1 text-xs font-bold uppercase tracking-[0.22em] text-amber-800">
              Step 1
            </p>
            <h2 className="text-2xl font-semibold text-slate-950">Create Envelope</h2>
          </div>

          <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
            <span>Subject</span>
            <input
              className={inputClasses()}
              value={form.subject}
              onChange={(event) =>
                setForm((current) => ({ ...current, subject: event.target.value }))
              }
            />
          </label>

          <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
            <span>Message</span>
            <textarea
              className={textareaClasses()}
              value={form.message}
              onChange={(event) =>
                setForm((current) => ({ ...current, message: event.target.value }))
              }
            />
          </label>

          <div className="grid gap-4 md:grid-cols-2">
            <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
              <span>Signer name</span>
              <input
                className={inputClasses()}
                value={form.signerName}
                onChange={(event) =>
                  setForm((current) => ({ ...current, signerName: event.target.value }))
                }
                placeholder="Pat Doe"
              />
            </label>

            <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
              <span>Role key</span>
              <input
                className={inputClasses()}
                value={form.roleKey}
                onChange={(event) =>
                  setForm((current) => ({ ...current, roleKey: event.target.value }))
                }
                placeholder="s1"
              />
            </label>
          </div>

          <label className="grid gap-2 text-sm font-semibold text-slate-800">
            <span>PDF file</span>
            <input
              className="block w-full rounded-2xl border border-dashed border-slate-300 bg-white px-4 py-5 text-sm text-slate-700 file:mr-4 file:rounded-full file:border-0 file:bg-slate-950 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-white"
              type="file"
              accept=".pdf,application/pdf"
              onChange={(event) =>
                setForm((current) => ({ ...current, file: event.target.files?.[0] ?? null }))
              }
            />
          </label>

          {errorMessage ? (
            <div className="rounded-[1.5rem] bg-rose-50 p-4 text-rose-900">
              <p className="font-semibold">E-sign request failed.</p>
              <pre className="mt-3 overflow-x-auto whitespace-pre-wrap text-sm">{errorMessage}</pre>
            </div>
          ) : null}

          <div className="flex flex-wrap gap-3">
            <button
              className={buttonClasses()}
              disabled={!canSubmit || createEnvelopeMutation.isPending}
              type="submit"
            >
              {createEnvelopeMutation.isPending ? "Creating..." : "Create and Launch Signing"}
            </button>
            <button
              className={buttonClasses("secondary")}
              disabled={!search.envelopeId || envelopeQuery.isFetching}
              type="button"
              onClick={() => void envelopeQuery.refetch()}
            >
              {envelopeQuery.isFetching ? "Refreshing..." : "Refresh Envelope"}
            </button>
          </div>
        </form>

        <div className="grid gap-5">
          <section className={cardClasses("bg-white/82")}>
            <div className="mb-4">
              <p className="mb-1 text-xs font-bold uppercase tracking-[0.22em] text-amber-800">
                Step 2
              </p>
              <h2 className="text-2xl font-semibold text-slate-950">Embedded Signing</h2>
            </div>
            {signingEvent ? (
              <div className="mb-4 rounded-[1.5rem] border border-teal-200 bg-teal-50 px-4 py-3 text-sm text-teal-950">
                DocuSign returned event:
                {" "}
                <span className="font-semibold">{signingEvent}</span>
              </div>
            ) : null}
            {search.signingUrl ? (
              <div className="grid gap-4">
                <div className="overflow-hidden rounded-[1.75rem] border border-slate-200 bg-slate-950/95 shadow-[0_18px_40px_rgba(15,23,42,0.14)]">
                  <iframe
                    className="block h-[720px] w-full bg-white"
                    src={search.signingUrl}
                    title="DocuSign embedded signing"
                  />
                </div>
                <div className="flex flex-wrap gap-3">
                  <button
                    className={buttonClasses("secondary")}
                    type="button"
                    onClick={() => setSigningEvent(null)}
                  >
                    Clear Event Banner
                  </button>
                  <a
                    className="rounded-full border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:border-slate-400"
                    href={search.signingUrl}
                    rel="noreferrer"
                    target="_blank"
                  >
                    Open Fallback Window
                  </a>
                </div>
              </div>
            ) : (
              <p className="text-sm leading-6 text-slate-600">
                Create an envelope first. The embedded signing ceremony will appear here once the
                recipient view is created.
              </p>
            )}
          </section>

          <section className={cardClasses("bg-white/82")}>
            <div className="mb-4">
              <p className="mb-1 text-xs font-bold uppercase tracking-[0.22em] text-amber-800">
                Envelope
              </p>
              <h2 className="text-2xl font-semibold text-slate-950">Current Status</h2>
            </div>
            {envelopeQuery.isLoading ? (
              <p className="text-sm leading-6 text-slate-600">Loading envelope...</p>
            ) : envelopeQuery.data ? (
              <dl className="grid gap-3">
                <SummaryRow
                  label="Status"
                  value={String(envelopeQuery.data.status ?? "n/a")}
                />
                <SummaryRow
                  label="Envelope ID"
                  value={String(envelopeQuery.data.id ?? "n/a")}
                />
                <SummaryRow
                  label="Provider ID"
                  value={String(envelopeQuery.data.externalEnvelopeId ?? "n/a")}
                />
                <SummaryRow
                  label="Signer"
                  value={firstSigner?.fullName ?? "n/a"}
                />
                <SummaryRow
                  label="Signer Status"
                  value={String(firstSigner?.status ?? "n/a")}
                />
                <SummaryRow
                  label="Updated"
                  value={formatDateTime(envelopeQuery.data.lastProviderUpdateAt)}
                />
              </dl>
            ) : (
              <p className="text-sm leading-6 text-slate-600">
                No envelope has been created yet.
              </p>
            )}
          </section>
        </div>
      </div>
    </section>
  );
}
