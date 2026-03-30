import { Link, Outlet, useRouterState } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import {
  clearBasicAuth,
  loadBasicAuth,
  saveBasicAuth,
  type BasicAuthCredentials,
} from "./lib/http";
import { cardClasses, inputClasses, PageIntro } from "./routes/document-templates/shared";

type NavItem = {
  to: "/document-templates" | "/document-templates/new" | "/esign";
  label: string;
  description: string;
};

const navItems: NavItem[] = [
  {
    to: "/document-templates",
    label: "Search Templates",
    description: "Browse uploaded templates and open a template-specific generate screen.",
  },
  {
    to: "/document-templates/new",
    label: "Create Template",
    description: "Upload a new DOCX or PDF template and inspect its discovered form map.",
  },
  {
    to: "/esign",
    label: "In-Person E-Sign",
    description: "Upload a PDF, create an in-person envelope, and launch the embedded signing flow.",
  },
];

export default function App() {
  const pathname = useRouterState({ select: (state) => state.location.pathname });
  const [auth, setAuth] = useState<BasicAuthCredentials>({ username: "", password: "" });
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  useEffect(() => {
    const stored = loadBasicAuth();
    if (stored) {
      setAuth(stored);
    }
  }, []);

  function handleSave() {
    saveBasicAuth(auth);
    setSavedMessage("Saved locally for this browser.");
  }

  function handleClear() {
    clearBasicAuth();
    setAuth({ username: "", password: "" });
    setSavedMessage("Cleared.");
  }

  return (
    <main className="mx-auto min-h-screen w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <section className="mb-6 grid gap-5 lg:grid-cols-[1.35fr_0.85fr]">
        <PageIntro
          eyebrow="WF Sandbox"
          title="Document Templates"
          description="Search templates, upload new ones, and generate merged output from a route that renders fields directly from the template form map."
        />

        <div className={cardClasses("bg-white/80")}>
          <h2 className="mb-4 text-lg font-semibold text-slate-950">Basic Auth</h2>
          <div className="grid gap-4">
            <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
              <span>Username</span>
              <input
                className={inputClasses()}
                placeholder="bob"
                value={auth.username}
                onChange={(event) =>
                  setAuth((current) => ({ ...current, username: event.target.value }))
                }
              />
            </label>

            <label className="grid gap-1.5 text-sm font-semibold text-slate-800">
              <span>Password</span>
              <input
                className={inputClasses()}
                type="password"
                placeholder="bob"
                value={auth.password}
                onChange={(event) =>
                  setAuth((current) => ({ ...current, password: event.target.value }))
                }
              />
            </label>

            <div className="flex flex-wrap gap-3">
              <button
                className="rounded-full bg-slate-950 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800"
                type="button"
                onClick={handleSave}
              >
                Save Credentials
              </button>
              <button
                className="rounded-full border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:border-slate-400"
                type="button"
                onClick={handleClear}
              >
                Clear
              </button>
            </div>

            <p className="text-sm leading-6 text-slate-500">
              API requests use whatever is currently stored in localStorage.
            </p>
            {savedMessage ? <p className="text-sm font-medium text-teal-800">{savedMessage}</p> : null}
          </div>
        </div>
      </section>

      <nav className="mb-6 grid gap-4 md:grid-cols-2">
        {navItems.map((item) => {
          const active = pathname === item.to;
          return (
            <Link
              key={item.to}
              to={item.to}
              search={item.to === "/document-templates" ? { page: 0, size: 12 } : undefined}
              className={`rounded-[1.75rem] border p-5 shadow-[0_14px_40px_rgba(44,55,54,0.08)] transition ${
                active
                  ? "border-teal-700 bg-teal-950 text-white"
                  : "border-black/8 bg-white/75 text-slate-900 hover:border-teal-700/35"
              }`}
            >
              <div className="mb-2 text-xs font-bold uppercase tracking-[0.22em] text-amber-700/90">
                Route
              </div>
              <h2 className="mb-2 text-xl font-semibold">{item.label}</h2>
              <p className={active ? "text-sm leading-6 text-white/82" : "text-sm leading-6 text-slate-600"}>
                {item.description}
              </p>
            </Link>
          );
        })}
      </nav>

      <Outlet />
    </main>
  );
}
