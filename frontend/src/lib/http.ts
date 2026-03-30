const AUTH_STORAGE_KEY = "wf-sandbox-basic-auth";

export type BasicAuthCredentials = {
  username: string;
  password: string;
};

export function loadBasicAuth(): BasicAuthCredentials | null {
  const rawValue = window.localStorage.getItem(AUTH_STORAGE_KEY);
  if (!rawValue) {
    return null;
  }
  try {
    const parsed = JSON.parse(rawValue) as Partial<BasicAuthCredentials>;
    if (!parsed.username || !parsed.password) {
      return null;
    }
    return { username: parsed.username, password: parsed.password };
  } catch {
    return null;
  }
}

export function saveBasicAuth(credentials: BasicAuthCredentials) {
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(credentials));
}

export function clearBasicAuth() {
  window.localStorage.removeItem(AUTH_STORAGE_KEY);
}

export type RequestConfig = {
  url: string;
  method?: string;
  headers?: Record<string, string>;
  params?: Record<string, unknown>;
  responseType?: "json" | "text" | "blob";
  data?: BodyInit | object | null;
  signal?: AbortSignal;
};

function normalizeMultipartJsonParts(body: BodyInit | undefined) {
  if (!(body instanceof FormData)) {
    return body;
  }

  const requestPart = body.get("request");
  if (typeof requestPart !== "string") {
    return body;
  }

  body.set("request", new Blob([requestPart], { type: "application/json" }));
  return body;
}

function toSearchParams(params?: Record<string, unknown>) {
  const searchParams = new URLSearchParams();
  if (!params) {
    return searchParams;
  }
  for (const [key, value] of Object.entries(params)) {
    if (value == null) {
      continue;
    }
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item != null) {
          searchParams.append(key, String(item));
        }
      }
      continue;
    }
    searchParams.set(key, String(value));
  }
  return searchParams;
}

function withBaseUrl(url: string, params?: Record<string, unknown>) {
  if (/^https?:\/\//.test(url)) {
    const parsed = new URL(url);
    const searchParams = toSearchParams(params);
    searchParams.forEach((value, key) => parsed.searchParams.append(key, value));
    return parsed.toString();
  }
  const searchParams = toSearchParams(params);
  const queryString = searchParams.toString();
  return queryString ? `${url}?${queryString}` : url;
}

function toRequestInit(config: RequestConfig): RequestInit {
  const headers = new Headers(config.headers ?? {});
  const credentials = loadBasicAuth();
  if (credentials) {
    headers.set(
      "Authorization",
      `Basic ${window.btoa(`${credentials.username}:${credentials.password}`)}`,
    );
  }

  let body: BodyInit | undefined;
  if (config.data instanceof FormData || config.data instanceof Blob) {
    body = config.data;
  } else if (typeof config.data === "string") {
    body = config.data;
  } else if (config.data != null) {
    if (!headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    body = JSON.stringify(config.data);
  }

  if (body instanceof FormData) {
    headers.delete("Content-Type");
  }

  body = normalizeMultipartJsonParts(body);

  return {
    method: config.method ?? "GET",
    headers,
    body,
    signal: config.signal,
  };
}

async function parseResponse(
  response: Response,
  responseType?: RequestConfig["responseType"],
) {
  if (responseType === "blob") {
    return response.blob();
  }
  if (responseType === "text") {
    return response.text();
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    return response.json();
  }
  if (contentType.startsWith("text/")) {
    return response.text();
  }
  if (contentType.includes("application/pdf") || contentType.includes("application/octet-stream")) {
    return response.blob();
  }
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export async function customInstance<TData>(config: RequestConfig): Promise<TData> {
  const response = await fetch(withBaseUrl(config.url, config.params), toRequestInit(config));
  if (!response.ok) {
    const payload = await parseResponse(response, config.responseType).catch(() => null);
    const error = new Error(response.statusText) as Error & {
      status: number;
      payload: unknown;
    };
    error.status = response.status;
    error.payload = payload;
    throw error;
  }
  return (await parseResponse(response, config.responseType)) as TData;
}
