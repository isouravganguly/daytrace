#!/usr/bin/env node
// Pull your thoughts from Supabase into a folder of Markdown files, one per month,
// ready for Claude Code (or any LLM) to read, search and summarise.
//
// Usage:
//   HTT_EMAIL=you@example.com HTT_PASSWORD=... node tools/export-thoughts.mjs [outDir]
//
// SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY are read from the environment, or from
// ../local.properties (the same file the Android build uses). No npm install needed.

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const outDir = resolve(process.argv[2] ?? join(process.env.HOME, "HoldThisThought"));

async function config() {
  const props = {};
  try {
    const text = await readFile(join(root, "local.properties"), "utf8");
    for (const line of text.split("\n")) {
      const m = line.match(/^\s*([A-Z_]+)\s*=\s*(.*)\s*$/);
      if (m) props[m[1]] = m[2];
    }
  } catch {
    // local.properties is optional when env vars are set.
  }
  const get = (k) => process.env[k] ?? props[k];
  const cfg = {
    url: get("SUPABASE_URL"),
    key: get("SUPABASE_PUBLISHABLE_KEY"),
    email: process.env.HTT_EMAIL,
    password: process.env.HTT_PASSWORD,
  };
  const missing = Object.entries(cfg).filter(([, v]) => !v).map(([k]) => k);
  if (missing.length) {
    console.error(`Missing: ${missing.join(", ")}. See the header of this file.`);
    process.exit(1);
  }
  return cfg;
}

async function signIn({ url, key, email, password }) {
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: { apikey: key, "content-type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw new Error(`Sign-in failed (${res.status}): ${await res.text()}`);
  return (await res.json()).access_token;
}

async function fetchThoughts({ url, key }, token) {
  const all = [];
  const pageSize = 1000;
  for (let from = 0; ; from += pageSize) {
    const res = await fetch(
      `${url}/rest/v1/thoughts?select=id,body,kind,space,title,tags,created_at,completed_at,input` +
        `&deleted_at=is.null&order=created_at.asc`,
      {
        headers: {
          apikey: key,
          authorization: `Bearer ${token}`,
          range: `${from}-${from + pageSize - 1}`,
        },
      },
    );
    if (!res.ok) throw new Error(`Fetch failed (${res.status}): ${await res.text()}`);
    const page = await res.json();
    all.push(...page);
    if (page.length < pageSize) return all;
  }
}

const label = { THOUGHT: "Thought", LEARNING: "Learning", TODO: "Todo" };

function entry(t) {
  const d = new Date(t.created_at);
  const time = d.toTimeString().slice(0, 5);
  const status = t.kind === "TODO" ? (t.completed_at ? " · done" : " · open") : "";
  const lines = [`### ${time} · ${label[t.kind] ?? t.kind} · ${t.space.toLowerCase()}${status}` +
    (t.title ? ` — ${t.title}` : "")];
  if (t.tags?.length) lines.push(`Tags: ${t.tags.map((x) => `#${x}`).join(" ")}`, "");
  lines.push(t.body, "");
  return lines.join("\n");
}

function localDay(iso) {
  const d = new Date(iso);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

const GUIDE = `# Hold This Thought — my captured thoughts

This folder is an export of my personal thought collector. One file per month
(\`YYYY-MM.md\`), entries grouped by day, oldest first. \`learnings.md\` collects every
LEARNING in one place.

Each entry header is: \`time · kind · space [· todo status] — AI title\`, followed by
optional AI tags and then my own words, unedited.

- THOUGHT: ideas and reflections
- LEARNING: things I learned and don't want to relearn
- TODO: actions (open or done)

When I ask about this folder:
- Quote my original words when citing an entry, with its date.
- Prefer LEARNING entries for "what do I know about X" questions.
- Point out when I seem to have learned the same thing more than once.
`;

async function main() {
  const cfg = await config();
  const token = await signIn(cfg);
  const thoughts = await fetchThoughts(cfg, token);

  const byMonth = new Map();
  for (const t of thoughts) {
    const month = localDay(t.created_at).slice(0, 7);
    if (!byMonth.has(month)) byMonth.set(month, []);
    byMonth.get(month).push(t);
  }

  await mkdir(outDir, { recursive: true });
  for (const [month, items] of byMonth) {
    const parts = [`# ${month}`, ""];
    let day = "";
    for (const t of items) {
      const d = localDay(t.created_at);
      if (d !== day) parts.push(`## ${(day = d)}`, "");
      parts.push(entry(t));
    }
    await writeFile(join(outDir, `${month}.md`), parts.join("\n"));
  }

  const learnings = thoughts.filter((t) => t.kind === "LEARNING");
  await writeFile(
    join(outDir, "learnings.md"),
    ["# All learnings", "", ...learnings.map((t) => `## ${localDay(t.created_at)}\n\n${entry(t)}`)].join("\n"),
  );
  await writeFile(join(outDir, "CLAUDE.md"), GUIDE);

  console.log(`Wrote ${thoughts.length} entries (${learnings.length} learnings) to ${outDir}`);
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
