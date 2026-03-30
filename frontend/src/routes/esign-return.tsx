import { useSearch } from "@tanstack/react-router";
import { useEffect } from "react";
import { cardClasses, PageIntro } from "./document-templates/shared";

export type EsignReturnSearch = {
  event?: string;
};

export function validateEsignReturnSearch(search: Record<string, unknown>): EsignReturnSearch {
  return {
    event: typeof search.event === "string" ? search.event : undefined,
  };
}

export function EsignReturnPage() {
  const search = useSearch({ from: "/esign/return" });

  useEffect(() => {
    if (window.parent === window) {
      return;
    }
    window.parent.postMessage(
      {
        type: "docusign-return",
        event: search.event ?? null,
      },
      window.location.origin,
    );
  }, [search.event]);

  return (
    <section className="grid gap-5">
      <PageIntro
        eyebrow="E-Sign"
        title="Signing Session Returned"
        description="This route handles the DocuSign return URL after an embedded signing ceremony exits."
      />

      <section className={cardClasses("bg-white/82")}>
        <h2 className="text-2xl font-semibold text-slate-950">Return Event</h2>
        <p className="mt-4 text-sm leading-6 text-slate-600">
          DocuSign returned control to the app with event:
          {" "}
          <span className="font-semibold text-slate-900">{search.event ?? "unknown"}</span>
        </p>
        <p className="mt-3 text-sm leading-6 text-slate-600">
          If this page opened inside the signing iframe, the parent window has already been notified.
          You can close this view if it remains visible.
        </p>
      </section>
    </section>
  );
}
