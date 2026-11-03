import { ApiError, FieldError } from '../Models/HttpModels';
import { infoStore } from '../Utils/InfoStore';

type QueryParams = Record<string, string | number | undefined>;

export function hasAuthToken(): boolean {
  return infoStore.getAuthToken() !== null;
}

export function withQuery(url: string, params?: QueryParams): string {
  if (!params) return url;
  const q = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') q.set(key, String(value));
  }
  const query = q.toString();
  return query ? `${url}?${query}` : url;
}

function buildHeaders(authRequired: boolean, hasBody: boolean, extra: Record<string, string> = {}) {
  const headers: Record<string, string> = { Accept: 'application/json', ...extra };
  if (hasBody) headers['Content-Type'] = 'application/json';
  const token = authRequired ? infoStore.getAuthToken() : null;
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

async function readJson(res: Response): Promise<unknown> {
  const text = await res.text().catch(() => '');
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

async function send<T>(
  method: 'GET' | 'POST' | 'PUT',
  url: string,
  authRequired: boolean,
  body?: unknown,
  extraHeaders?: Record<string, string>,
): Promise<T> {
  let res: Response;
  try {
    res = await fetch(url, {
      method,
      headers: buildHeaders(authRequired, body !== undefined, extraHeaders),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'NETWORK', "Can't reach the server. Check your connection and try again.");
  }

  const data = await readJson(res);
  if (res.ok) return data as T;

  const err = data as { error?: string; message?: string; fieldErrors?: FieldError[] } | null;
  throw new ApiError(
    res.status,
    err?.error ?? 'HTTP_' + res.status,
    err?.message ?? `Request failed (${res.status})`,
    err?.fieldErrors ?? [],
  );
}

export async function httpGet<T>(url: string, authRequired = false): Promise<T> {
  return send<T>('GET', url, authRequired);
}

export async function httpPost<T>(
  url: string,
  authRequired = false,
  body: unknown = {},
  headers?: Record<string, string>,
): Promise<T> {
  return send<T>('POST', url, authRequired, body, headers);
}

export async function httpPut<T>(url: string, authRequired = false, body: unknown = {}): Promise<T> {
  return send<T>('PUT', url, authRequired, body);
}
