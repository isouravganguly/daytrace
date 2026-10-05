// thought-machine: AI enrichment and semantic search for Hold This Thought.
//
// Actions (POST JSON, called with the user's JWT so Row Level Security applies):
//   { "action": "enrich_pending" }        classify, title, tag and embed up to BATCH rows not yet enriched
//   { "action": "search", "query": "…" }  ids of the user's thoughts closest in meaning to the query
//
// The user's original text is never modified. A kind chosen by the user (kind_source = USER)
// is never overridden.

import { GoogleGenAI } from "npm:@google/genai@2.3.0";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";

const geminiKey = Deno.env.get("GEMINI_API_KEY");
const supabaseUrl = Deno.env.get("SUPABASE_URL");
const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");

if (!geminiKey || !supabaseUrl || !supabaseAnonKey) {
  throw new Error("Required Edge Function environment variables are missing.");
}

const ai = new GoogleGenAI({ apiKey: geminiKey });
const CLASSIFY_MODEL = "gemini-3.1-flash-lite";
const EMBED_MODEL = "gemini-embedding-2";
const DIMENSIONS = 768;
const BATCH = 20;
const KINDS = ["THOUGHT", "LEARNING", "TODO"] as const;

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

async function embed(text: string, taskType: "RETRIEVAL_DOCUMENT" | "RETRIEVAL_QUERY") {
  const result = await ai.models.embedContent({
    model: EMBED_MODEL,
    contents: text,
    config: { outputDimensionality: DIMENSIONS, taskType },
  });
  const values = result.embeddings?.[0]?.values;
  if (!values || values.length !== DIMENSIONS) throw new Error("Invalid embedding from Gemini.");
  return values;
}

type Enrichment = { kind: (typeof KINDS)[number]; title: string; tags: string[] };

async function analyse(body: string): Promise<Enrichment> {
  const result = await ai.models.generateContent({
    model: CLASSIFY_MODEL,
    contents: body,
    config: {
      systemInstruction: [
        "You organise a person's private captured notes. Never invent facts.",
        "kind: TODO if the speaker intends to do something; LEARNING if it records something",
        "they learned, a fact, technique, lesson or insight worth remembering; otherwise THOUGHT.",
        "title: 3 to 8 words capturing the gist, in plain language, no trailing period.",
        "tags: 1 to 4 short lowercase topic tags (e.g. kotlin, postgres, health, career),",
        "no '#', hyphenate multi-word tags.",
      ].join(" "),
      responseMimeType: "application/json",
      responseJsonSchema: {
        type: "object",
        properties: {
          kind: { type: "string", enum: KINDS },
          title: { type: "string" },
          tags: { type: "array", items: { type: "string" }, maxItems: 4 },
        },
        required: ["kind", "title", "tags"],
        additionalProperties: false,
      },
    },
  });
  const parsed = JSON.parse(result.text ?? "{}");
  if (!KINDS.includes(parsed.kind)) throw new Error("Invalid classification from Gemini.");
  return {
    kind: parsed.kind,
    title: String(parsed.title ?? "").trim().slice(0, 120),
    tags: (Array.isArray(parsed.tags) ? parsed.tags : [])
      .map((t: unknown) => String(t).toLowerCase().replace(/^#/, "").trim().replace(/\s+/g, "-"))
      .filter((t: string) => t.length > 0 && t.length <= 32)
      .slice(0, 4),
  };
}

async function enrichPending(supabase: SupabaseClient) {
  const { data: rows, error } = await supabase
    .from("thoughts")
    .select("id, body, kind_source")
    .is("enriched_at", null)
    .is("deleted_at", null)
    .order("created_at", { ascending: true })
    .limit(BATCH);
  if (error) throw error;

  let enriched = 0;
  const failed: string[] = [];
  for (const row of rows ?? []) {
    try {
      const [analysis, embedding] = await Promise.all([
        analyse(row.body),
        embed(row.body, "RETRIEVAL_DOCUMENT"),
      ]);
      const update: Record<string, unknown> = {
        title: analysis.title || null,
        tags: analysis.tags,
        embedding,
        enriched_at: new Date().toISOString(),
      };
      if (row.kind_source !== "USER") update.kind = analysis.kind;
      const { error: updateError } = await supabase
        .from("thoughts")
        .update(update)
        .eq("id", row.id);
      if (updateError) throw updateError;
      enriched++;
    } catch (e) {
      // One bad row must not block the rest; it stays pending and is retried next sync.
      console.error(`enrich failed for ${row.id}`, e);
      failed.push(row.id);
    }
  }
  return { enriched, failed: failed.length, remaining: (rows?.length ?? 0) === BATCH };
}

async function search(supabase: SupabaseClient, query: string) {
  const embedding = await embed(query, "RETRIEVAL_QUERY");
  const { data, error } = await supabase.rpc("match_thoughts", {
    query_embedding: embedding,
    match_count: 12,
  });
  if (error) throw error;
  // Drop weak matches so "related" stays meaningful.
  return (data ?? []).filter((r: { similarity: number }) => r.similarity >= 0.5);
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return response({ error: "POST required" }, 405);

  const authorization = req.headers.get("authorization");
  if (!authorization) return response({ error: "Authentication required" }, 401);

  const supabase = createClient(supabaseUrl, supabaseAnonKey, {
    global: { headers: { Authorization: authorization } },
  });

  try {
    const input = await req.json();
    switch (input.action) {
      case "enrich_pending":
        return response(await enrichPending(supabase));
      case "search": {
        const query = typeof input.query === "string" ? input.query.trim() : "";
        if (!query) return response({ error: "query is required" }, 400);
        return response({ results: await search(supabase, query) });
      }
      default:
        return response({ error: "Unknown action" }, 400);
    }
  } catch (error) {
    console.error(error);
    return response({ error: "Unable to process request" }, 500);
  }
});
