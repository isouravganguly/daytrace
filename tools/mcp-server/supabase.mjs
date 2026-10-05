// Minimal Supabase client for the MCP server: config, a persisted login session with
// automatic token refresh, and the few REST / Edge Function calls the tools need.

import { chmod, mkdir, readFile, writeFile } from "node:fs/promises";
import { homedir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const sessionPath = process.env.HTT_SESSION_FILE ??
  join(homedir(), ".config", "hold-this-thought", "session.json");

export async function loadConfig() {
  const props = {};
  try {
    const text = await readFile(join(repoRoot, "local.properties"), "utf8");
    for (const line of text.split("\n")) {
      const m = line.match(/^\s*([A-Z_]+)\s*=\s*(.*?)\s*$/);
      if (m) props[m[1]] = m[2];
    }
  } catch {
    // Optional when the environment provides the values.
  }
  const url = process.env.SUPABASE_URL ?? props.SUPABASE_URL;
  const key = process.env.SUPABASE_PUBLISHABLE_KEY ?? props.SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key) {
    throw new Error(
      "SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY are missing. Set them in the environment " +
        "or in the repo's local.properties.",
    );
  }
  return { url: url.replace(/\/$/, ""), key };
}

export class SupabaseSession {
  #config;
  #session = null;
  #refreshing = null;

  constructor(config) {
    this.#config = config;
  }

  get userId() {
    return this.#session?.user_id;
  }

  /** Password sign-in; stores only the tokens, never the password. */
  async login(email, password) {
    const body = await this.#authRequest("password", { email, password });
    await this.#save(body);
    return body.user?.email ?? email;
  }

  async #authRequest(grantType, payload) {
    const res = await fetch(`${this.#config.url}/auth/v1/token?grant_type=${grantType}`, {
      method: "POST",
      headers: { apikey: this.#config.key, "content-type": "application/json" },
      body: JSON.stringify(payload),
    });
    const text = await res.text();
    if (!res.ok) {
      let message = text;
      try {
        const parsed = JSON.parse(text);
        message = parsed.error_description ?? parsed.msg ?? parsed.message ?? text;
      } catch {
        // keep raw text
      }
      throw new Error(`Supabase auth failed (${res.status}): ${message}`);
    }
    return JSON.parse(text);
  }

  async #save(body) {
    this.#session = {
      access_token: body.access_token,
      refresh_token: body.refresh_token,
      expires_at: body.expires_at ?? Math.floor(Date.now() / 1000) + (body.expires_in ?? 3600),
      user_id: body.user?.id,
      email: body.user?.email,
    };
    await mkdir(dirname(sessionPath), { recursive: true, mode: 0o700 });
    await writeFile(sessionPath, JSON.stringify(this.#session, null, 2), { mode: 0o600 });
    await chmod(sessionPath, 0o600);
  }

  /** Throws a helpful error if `login` has never been run. Does not touch the network. */
  async requireLogin() {
    await this.#load();
  }

  async #load() {
    if (this.#session) return;
    try {
      this.#session = JSON.parse(await readFile(sessionPath, "utf8"));
    } catch {
      throw new Error(
        "Not logged in. Run `npm run login` in tools/mcp-server (needs an email account; " +
          "an anonymous app session must first be secured in the app's Settings).",
      );
    }
  }

  /** A valid access token, refreshing (and persisting the rotated refresh token) when needed. */
  async accessToken() {
    await this.#load();
    const fresh = this.#session.expires_at - 60 > Date.now() / 1000;
    if (fresh) return this.#session.access_token;
    this.#refreshing ??= this.#authRequest("refresh_token", {
      refresh_token: this.#session.refresh_token,
    })
      .then((body) => this.#save(body))
      .finally(() => {
        this.#refreshing = null;
      });
    await this.#refreshing;
    return this.#session.access_token;
  }

  async #request(path, { method = "GET", body, headers = {} } = {}) {
    const token = await this.accessToken();
    const res = await fetch(`${this.#config.url}${path}`, {
      method,
      headers: {
        apikey: this.#config.key,
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    if (!res.ok) throw new Error(`Supabase request failed (${res.status}): ${text.slice(0, 300)}`);
    return text ? JSON.parse(text) : null;
  }

  select(query) {
    return this.#request(`/rest/v1/thoughts?${query}`);
  }

  insert(row) {
    return this.#request("/rest/v1/thoughts", {
      method: "POST",
      body: row,
      headers: { prefer: "return=representation" },
    });
  }

  update(id, patch) {
    return this.#request(`/rest/v1/thoughts?id=eq.${encodeURIComponent(id)}`, {
      method: "PATCH",
      body: patch,
      headers: { prefer: "return=representation" },
    });
  }

  invoke(payload) {
    return this.#request("/functions/v1/thought-machine", { method: "POST", body: payload });
  }
}
