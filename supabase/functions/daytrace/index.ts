import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import postgres from "npm:postgres@3.4.4";

// daytrace: per-device collectors (Mac, phone) post here with `x-device-token`.
//   POST /wakeup   {"at"?: ISO} log a wake-up at the tap time (trusted within 12 h, else server
//                  time); repeat taps within 4 h don't duplicate
//   POST /ingest   {"intervals":[{app, started_at, ended_at}]}  foreground-app intervals
//   POST /health   {"days":[{day, steps, walk_minutes, exercise_minutes, sleep_minutes,
//                   sleep_start, sleep_end, meals, calories}]}  daily Health Connect aggregates
//   POST /register {install_id, name, platform, kind} with the owner's Supabase session
//                  (Authorization: Bearer <jwt>) -> {"token": "..."}; the app stores it silently
//
// Auth is a per-device token (hashed in daytrace.devices), checked inside the DB functions,
// so the platform JWT check is off for this function.
const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { max: 1, prepare: false });

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;

/** The signed-in user behind a Supabase access token, or null. */
async function userFrom(authorization: string | null): Promise<string | null> {
  if (!authorization?.startsWith("Bearer ")) return null;
  const res = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: { Authorization: authorization, apikey: anonKey },
  });
  if (!res.ok) return null;
  const user = await res.json();
  return user?.is_anonymous ? null : (user?.id ?? null);
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "POST only" }, 405);

  const route = new URL(req.url).pathname.split("/").filter(Boolean).pop();

  if (route === "register") {
    try {
      const userId = await userFrom(req.headers.get("authorization"));
      if (!userId) return json({ error: "sign in required" }, 401);
      const body = await req.json().catch(() => null);
      const kind = ["phone", "tablet", "laptop"].includes(body?.kind) ? body.kind : "phone";
      const rows = await sql`select daytrace.register_install(
        ${userId}, ${String(body?.install_id ?? "")}, ${String(body?.name ?? "Device").slice(0, 80)},
        ${String(body?.platform ?? "android").slice(0, 20)}, ${kind}) as token`;
      return json({ token: rows[0].token });
    } catch (e) {
      const code = (e as { code?: string }).code;
      if (code === "42501") return json({ error: "not the owner of this project" }, 403);
      if (code === "22023") return json({ error: "bad install id" }, 400);
      console.error(e);
      return json({ error: "server error" }, 500);
    }
  }

  const token = req.headers.get("x-device-token");
  if (!token) return json({ error: "missing token" }, 401);

  try {
    if (route === "ingest") {
      const body = await req.json().catch(() => null);
      if (!body || !Array.isArray(body.intervals)) {
        return json({ error: "intervals array required" }, 400);
      }
      const rows = await sql`select daytrace.ingest(${token}, ${sql.json(body.intervals)}) as n`;
      return json({ ok: true, stored: rows[0].n });
    }

    if (route === "health") {
      const body = await req.json().catch(() => null);
      if (!body || !Array.isArray(body.days)) {
        return json({ error: "days array required" }, 400);
      }
      const rows = await sql`select daytrace.ingest_health(${token}, ${sql.json(body.days)}) as n`;
      return json({ ok: true, stored: rows[0].n });
    }

    if (route === "wakeup") {
      const body = await req.json().catch(() => null);
      const at = typeof body?.at === "string" && !Number.isNaN(Date.parse(body.at)) ? body.at : null;
      const rows = await sql`select woke_at, already_logged from daytrace.log_wakeup(${token}, ${at}::timestamptz)`;
      const r = rows[0];
      return json({
        greeting: r.already_logged ? "Good morning again" : "Good morning",
        woke_at: new Date(r.woke_at).toISOString(),
        already_logged: r.already_logged,
      });
    }

    return json({ error: "unknown route" }, 404);
  } catch (e) {
    const code = (e as { code?: string }).code;
    if (code === "28000") return json({ error: "invalid token" }, 401);
    if (code === "22023") return json({ error: "too many items" }, 400);
    console.error(e);
    return json({ error: "server error" }, 500);
  }
});
