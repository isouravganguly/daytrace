import { GoogleGenAI } from "npm:@google/genai@2.3.0";
import { createClient } from "npm:@supabase/supabase-js@2";

const geminiKey = Deno.env.get("GEMINI_API_KEY");
const supabaseUrl = Deno.env.get("SUPABASE_URL");
const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");

if (!geminiKey || !supabaseUrl || !supabaseAnonKey) {
  throw new Error("Required Edge Function environment variables are missing.");
}

const ai = new GoogleGenAI({ apiKey: geminiKey });

const jsonHeaders = { "content-type": "application/json" };

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: jsonHeaders });
}

async function embeddingFor(text: string) {
  const result = await ai.models.embedContent({
    model: "gemini-embedding-2",
    contents: text,
    config: {
      outputDimensionality: 768,
      taskType: "RETRIEVAL_DOCUMENT",
    },
  });
  const values = result.embeddings?.[0]?.values;
  if (!values || values.length !== 768) {
    throw new Error("Gemini returned an invalid embedding.");
  }
  return values;
}

async function queryEmbeddingFor(text: string) {
  const result = await ai.models.embedContent({
    model: "gemini-embedding-2",
    contents: text,
    config: {
      outputDimensionality: 768,
      taskType: "RETRIEVAL_QUERY",
    },
  });
  const values = result.embeddings?.[0]?.values;
  if (!values || values.length !== 768) {
    throw new Error("Gemini returned an invalid query embedding.");
  }
  return values;
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

    if (input.action === "enrich") {
      if (typeof input.thoughtId !== "string") {
        return response({ error: "thoughtId is required" }, 400);
      }

      const { data: thought, error: readError } = await supabase
        .from("thoughts")
        .select("id, body")
        .eq("id", input.thoughtId)
        .single();
      if (readError || !thought) return response({ error: "Thought not found" }, 404);

      const classification = await ai.models.generateContent({
        model: "gemini-3.1-flash-lite",
        contents: thought.body,
        config: {
          systemInstruction:
            "Classify the captured text as NOTE, TODO, or THOUGHT. " +
            "TODO means the speaker intends an action. NOTE means factual information " +
            "they want to retain. THOUGHT is an idea, reflection, or anything ambiguous. " +
            "Return only JSON matching the schema.",
          responseMimeType: "application/json",
          responseJsonSchema: {
            type: "object",
            properties: {
              kind: { type: "string", enum: ["NOTE", "TODO", "THOUGHT"] },
            },
            required: ["kind"],
            additionalProperties: false,
          },
        },
      });
      const kind = JSON.parse(classification.text ?? "{}").kind;
      if (!["NOTE", "TODO", "THOUGHT"].includes(kind)) {
        throw new Error("Gemini returned an invalid classification.");
      }

      const embedding = await embeddingFor(thought.body);
      const { error: updateError } = await supabase
        .from("thoughts")
        .update({ kind, embedding })
        .eq("id", thought.id);
      if (updateError) throw updateError;

      return response({ id: thought.id, kind });
    }

    if (input.action === "search") {
      const query = typeof input.query === "string" ? input.query.trim() : "";
      if (!query) return response({ error: "query is required" }, 400);

      const embedding = await queryEmbeddingFor(query);
      const { data, error } = await supabase.rpc("match_thoughts", {
        query_embedding: embedding,
        match_count: 12,
      });
      if (error) throw error;
      return response({ results: data ?? [] });
    }

    return response({ error: "Unknown action" }, 400);
  } catch (error) {
    console.error(error);
    return response({ error: "Unable to process thought" }, 500);
  }
});
