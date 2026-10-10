#!/usr/bin/env node
// Hold This Thought MCP server: lets Claude search, read and add to your thought collection.
//
//   node server.mjs login   one-time sign-in; stores a refresh token in ~/.config/hold-this-thought
//   node server.mjs         run as an MCP server over stdio (what Claude starts)
//
// Never write to stdout except through the MCP transport; log with console.error.

import { randomUUID } from "node:crypto";
import { createInterface } from "node:readline";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { loadConfig, SupabaseSession } from "./supabase.mjs";

const COLUMNS = "id,body,kind,kind_source,space,title,tags,created_at,completed_at";
const KINDS = ["THOUGHT", "LEARNING", "TODO", "DESIRE"];
const SPACES = ["WORK", "PERSONAL"];

// ---------- formatting ----------

function formatThought(t) {
  const when = new Date(t.created_at).toLocaleString("en-GB", {
    year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
  });
  const status = t.kind === "TODO" ? (t.completed_at ? " · done" : " · open") : "";
  const lines = [`[${t.kind}] ${when} · ${t.space.toLowerCase()}${status}${t.title ? ` — ${t.title}` : ""}`];
  if (t.tags?.length) lines.push(t.tags.map((x) => `#${x}`).join(" "));
  lines.push(t.body, `(id: ${t.id})`);
  return lines.join("\n");
}

function asText(thoughts, emptyMessage) {
  const text = thoughts.length ? thoughts.map(formatThought).join("\n\n") : emptyMessage;
  return { content: [{ type: "text", text }] };
}

function errorResult(error) {
  return { isError: true, content: [{ type: "text", text: error.message ?? String(error) }] };
}

/** Wraps a tool handler so failures come back to Claude as readable errors. */
const safe = (fn) => async (args) => {
  try {
    return await fn(args);
  } catch (error) {
    console.error(error);
    return errorResult(error);
  }
};

// ---------- data helpers ----------

/** Characters that would break PostgREST's or=(...) filter syntax. */
const literalTerm = (q) => q.replace(/[,()"\\*%:]/g, " ").replace(/\s+/g, " ").trim();

async function byIds(db, ids) {
  if (!ids.length) return [];
  const rows = await db.select(
    `select=${COLUMNS}&deleted_at=is.null&id=in.(${ids.map(encodeURIComponent).join(",")})`,
  );
  const byId = new Map(rows.map((r) => [r.id, r]));
  return ids.map((id) => byId.get(id)).filter(Boolean);
}

async function semanticIds(db, query) {
  try {
    const res = await db.invoke({ action: "search", query });
    return (res?.results ?? []).map((r) => r.id);
  } catch (error) {
    console.error("semantic search unavailable:", error.message);
    return [];
  }
}

async function literalMatches(db, query, limit) {
  const term = literalTerm(query);
  if (!term) return [];
  const pattern = encodeURIComponent(`*${term}*`);
  return db.select(
    `select=${COLUMNS}&deleted_at=is.null&or=(body.ilike.${pattern},title.ilike.${pattern})` +
      `&order=created_at.desc&limit=${limit}`,
  );
}

// ---------- server ----------

function buildServer(db) {
  const server = new McpServer({ name: "hold-this-thought", version: "0.1.0" });

  server.registerTool(
    "search_thoughts",
    {
      title: "Search thoughts",
      description:
        "Search the user's personal thought collection (thoughts, learnings, todos) by meaning " +
        "and by exact words. Use this whenever the user asks what they know, learned, noted or " +
        "thought about a topic, or before explaining something they may have already learned.",
      inputSchema: {
        query: z.string().min(1).describe("What to look for, in natural language"),
        kind: z.enum(KINDS).optional().describe("Only this kind"),
        limit: z.number().int().min(1).max(50).default(12),
      },
      annotations: { readOnlyHint: true },
    },
    safe(async ({ query, kind, limit }) => {
      const [semantic, literal] = await Promise.all([
        semanticIds(db, query).then((ids) => byIds(db, ids)),
        literalMatches(db, query, limit),
      ]);
      const seen = new Set();
      const merged = [...semantic, ...literal]
        .filter((t) => !seen.has(t.id) && seen.add(t.id))
        .filter((t) => !kind || t.kind === kind)
        .slice(0, limit);
      return asText(merged, `Nothing in the collection matches "${query}".`);
    }),
  );

  server.registerTool(
    "recent_thoughts",
    {
      title: "Recent thoughts",
      description:
        "List what the user captured recently, newest first. Use kind=LEARNING for questions like " +
        "'what did I learn this week' and for summaries or digests.",
      inputSchema: {
        days: z.number().int().min(1).max(365).default(7).describe("How far back to look"),
        kind: z.enum(KINDS).optional(),
        space: z.enum(SPACES).optional(),
        limit: z.number().int().min(1).max(200).default(50),
      },
      annotations: { readOnlyHint: true },
    },
    safe(async ({ days, kind, space, limit }) => {
      const since = new Date(Date.now() - days * 86_400_000).toISOString();
      const filters = [
        `select=${COLUMNS}`,
        "deleted_at=is.null",
        `created_at=gte.${encodeURIComponent(since)}`,
        kind && `kind=eq.${kind}`,
        space && `space=eq.${space}`,
        "order=created_at.desc",
        `limit=${limit}`,
      ].filter(Boolean);
      const rows = await db.select(filters.join("&"));
      return asText(rows, `Nothing captured in the last ${days} days.`);
    }),
  );

  server.registerTool(
    "list_todos",
    {
      title: "List todos",
      description: "List the user's todos from their thought collection. Open ones by default.",
      inputSchema: {
        include_done: z.boolean().default(false),
        space: z.enum(SPACES).optional(),
      },
      annotations: { readOnlyHint: true },
    },
    safe(async ({ include_done, space }) => {
      const filters = [
        `select=${COLUMNS}`,
        "deleted_at=is.null",
        "kind=eq.TODO",
        !include_done && "completed_at=is.null",
        space && `space=eq.${space}`,
        "order=created_at.desc",
        "limit=200",
      ].filter(Boolean);
      return asText(await db.select(filters.join("&")), "No open todos.");
    }),
  );

  server.registerTool(
    "add_thought",
    {
      title: "Add a thought",
      description:
        "Save something to the user's thought collection. Only call this when the user asks to " +
        "remember, note or save something. Store their words (or a faithful, concise version " +
        "they agreed to), not your own commentary. It syncs to their phone.",
      inputSchema: {
        body: z.string().min(1).max(10_000).describe("The text to keep"),
        kind: z.enum(KINDS).optional()
          .describe("Set only if clear; otherwise the server's AI classifies it"),
        space: z.enum(SPACES).default("WORK"),
      },
      annotations: { readOnlyHint: false, destructiveHint: false },
    },
    safe(async ({ body, kind, space }) => {
      const now = new Date().toISOString();
      const [row] = await db.insert({
        id: randomUUID(),
        user_id: db.userId,
        body: body.trim(),
        kind: kind ?? "THOUGHT",
        kind_source: kind ? "USER" : "AUTO",
        space,
        input: "TEXT",
        created_at: now,
        updated_at: now,
      });
      // Best effort: get it a title/tags (and a kind, if not given) right away.
      await db.invoke({ action: "enrich_pending" }).catch((e) => console.error(e.message));
      const [enriched] = await byIds(db, [row.id]);
      return asText([enriched ?? row], "Saved.");
    }),
  );

  server.registerTool(
    "complete_todo",
    {
      title: "Complete a todo",
      description: "Mark one of the user's todos as done (or reopen it). Needs the todo's id.",
      inputSchema: {
        id: z.string().uuid(),
        done: z.boolean().default(true),
      },
      annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: true },
    },
    safe(async ({ id, done }) => {
      const now = new Date().toISOString();
      const rows = await db.update(id, { completed_at: done ? now : null, updated_at: now });
      if (!rows?.length) return errorResult(new Error(`No thought with id ${id}.`));
      return asText(rows, "");
    }),
  );

  return server;
}

// ---------- CLI ----------

function prompt(question, { hidden = false } = {}) {
  return new Promise((resolve) => {
    const rl = createInterface({ input: process.stdin, output: process.stderr, terminal: true });
    if (hidden) {
      rl._writeToOutput = (s) => {
        if (s.startsWith(question)) process.stderr.write(question);
      };
    }
    rl.question(question, (answer) => {
      rl.close();
      if (hidden) process.stderr.write("\n");
      resolve(answer.trim());
    });
  });
}

async function login(db) {
  const email = process.env.HTT_EMAIL ?? (await prompt("Email: "));
  const password = process.env.HTT_PASSWORD ?? (await prompt("Password: ", { hidden: true }));
  const who = await db.login(email, password);
  console.error(`Logged in as ${who}. Claude can now use the hold-this-thought MCP server.`);
}

async function main() {
  const db = new SupabaseSession(await loadConfig());
  if (process.argv[2] === "login") return login(db);
  await db.requireLogin();
  await buildServer(db).connect(new StdioServerTransport());
  console.error("hold-this-thought MCP server running");
}

main().catch((error) => {
  console.error(error.message ?? error);
  process.exit(1);
});
